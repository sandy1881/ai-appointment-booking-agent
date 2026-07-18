# BrightCare Clinic — Appointment Booking Agent

A Telegram bot that books appointments for a (fictional) clinic. It understands free-text messages with Gemini, checks and writes real availability to Google Calendar, and emails the patient a confirmation once the booking goes through.

```
Telegram User
      │
      ▼
TelegramBot ──────────────► AgentOrchestratorService
                                   │        │
                          FaqService   IntentService (Gemini)
                                   │
                          BookingWorkflowService
                                   │        │
                     GoogleCalendarService  EmailService
                                   │        │
                          Google Calendar   SMTP
```

## Stack

- Java 21, Spring Boot 4.1, Maven
- Telegram via `telegrambots-springboot-longpolling-starter` — long polling, not a webhook
- Google Calendar via `google-api-client`, OAuth (installed-app flow, app in "Testing" status)
- Gemini (`gemini-flash-latest`), called with a plain Spring `RestClient` — no LLM SDK
- Email via Spring Mail over Gmail SMTP
- FAQs answered from a local JSON file, checked before Gemini is ever called

## Getting it running

You'll need Java 21, and accounts for Telegram, Google, and Gemini. Everything below goes into `.env` — copy `.env.example` to `.env` and fill it in as you go.

**Telegram bot**
Message [@BotFather](https://t.me/BotFather), run `/newbot`, and it'll hand you a username and a token. Drop those into `TELEGRAM_BOT_USERNAME` and `TELEGRAM_BOT_TOKEN`.

I went with long polling instead of a webhook — the bot just calls out to Telegram, so there's nothing to expose publicly and no ngrok/port-forwarding needed to run this on a laptop.

**Google Calendar**
Create a project in [Google Cloud Console](https://console.cloud.google.com), enable the Calendar API, and create an OAuth client of type **Desktop app**. Download the credentials JSON, save it at `config/credentials.json` (it's gitignored, don't commit it), and point `GOOGLE_CREDENTIALS_PATH` at it.

I picked OAuth over a service account mainly because it lets the app read/write *my own* calendar directly — a service account would need its own calendar shared to it first, which is more setup for the same result on a personal-account demo like this. The assignment explicitly allows an app in "Testing" status, so no Google verification needed.

First time you run the app and try to book something, it'll pop a browser (or print a login URL if it can't open one) to authorize access. After that the token's cached in `tokens/`, so it's a one-time thing. The Calendar client itself is wired up lazily — it doesn't get touched at startup, only the moment a booking actually needs it — so the app boots instantly instead of blocking on OAuth every time.

**Gemini**
Grab a free key at [aistudio.google.com](https://aistudio.google.com/apikey) and set `GEMINI_API_KEY`.

Heads up: the free tier caps out at 20 requests/day per model. I hit this a few times while testing — if the bot suddenly starts replying "having trouble understanding right now," that's a 429 from Gemini, not a bug. If a model name stops working, `gemini.model` in `application.yml` is the only place to change it.

**Email**
Turn on 2-Step Verification on the Gmail account you want to send from, then generate an App Password at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords). Use *that* for `EMAIL_PASSWORD`, not the real account password. `EMAIL_USERNAME` is the Gmail address.

**Then just run it:**

```bash
./mvnw spring-boot:run
```

`.env` loads automatically (via `springboot4-dotenv`), so nothing to export by hand. Message the bot on Telegram once it's up.

```bash
./mvnw test
```
runs the suite — 55 tests, all mocking out the actual Calendar/Gemini/SMTP calls so it doesn't need any of the above configured to run.

## How I built the conversation

**FAQ lookup happens before Gemini gets involved.** A message is checked against a small keyword list in `faq.json` first; if it matches, that's the answer, no API call made. Only a miss falls through to Gemini for intent classification. Cuts cost and latency for the handful of questions that have one fixed correct answer, and there's nothing for the model to hallucinate on those.

**The conversation is a state machine, not one big prompt.** Each chat has an explicit state — `GREETING`, `WAITING_FOR_BOOKING_DETAILS`, `WAITING_FOR_SLOT_CONFIRMATION`, `WAITING_FOR_EMAIL`, `BOOKING_COMPLETED`, plus a matching pair (`WAITING_FOR_CANCELLATION_DETAILS`, `WAITING_FOR_CANCELLATION_CONFIRMATION`) for cancelling — tracked per chat ID. Gemini only ever has to answer whatever's relevant to the current step — classify the intent, or pull a date/time out of a short reply — instead of having to reconstruct the whole conversation itself. That's really what makes "yes" reliably resolve to whatever slot was actually just proposed.

**Each integration is boxed in behind one class.** `GoogleCalendarService` is the only thing that knows about the Calendar API; `EmailService` is the only thing that knows about SMTP. `BookingWorkflowService` just calls `checkAvailability()` / `createAppointment()` / `sendAppointmentConfirmation()` — swapping either provider later is a one-class change, not a rewrite of the booking logic.

**"Nearest slot" only looks at the rest of the same day.** The spec is explicit about this — if nothing's free later that day, say so, don't quietly roll into tomorrow. So `findNextAvailableSlot` is bounded by the day's closing time and just returns "nothing left today" if it runs out.

**Business hours are checked before any calendar call goes out.** A weekend or after-hours request gets rejected immediately with a clear message instead of round-tripping to Google first.

**Concurrent bookings are actually handled, not just checked once and forgotten.** The gap between "checked available" and "confirmed" can be several messages long in a real conversation, so `createAppointment` re-checks for a conflict right before writing, and the whole check-then-write is behind a lock so two people confirming the same slot at nearly the same moment can't both get through. Whoever loses the race gets told the slot was just taken and gets asked for a new time — the loser doesn't just fail silently or double-book the calendar. I proved this actually does something by deliberately pulling the lock out and watching the concurrency test fail 8/8 times, then putting it back and watching it pass consistently.

**Global exception handling exists but has nothing to guard right now.** I built a `@RestControllerAdvice` while adding a couple of temporary `/calendar/*` test endpoints during development, mapping the various exception types to proper JSON error responses. Those test endpoints are gone now that I'm done with them, so the handler's just sitting there ready for whenever there's an actual REST surface again.

**Gemini gets the last few turns, not just the current message.** Each `UserSession` keeps a bounded rolling history (last 8 lines, ~4 exchanges) of what was said. Both the intent-classification prompt and the follow-up date/time extraction prompt include it, so a reply like "the later one" can actually be resolved against what the bot just offered, instead of being evaluated in a vacuum.

**Sessions survive a restart, via a flat file, not a database.** Every `saveSession` writes the whole session map to `data/sessions.json` (gitignored), and it's reloaded on startup. I went with a plain JSON file instead of Redis or an embedded database because this is a single-instance local app with a handful of small session objects — that's not a problem a database solves better, it's one a database adds ceremony to. I actually caught a real bug writing the test for this: my first version of the "restart" test passed a session that had never gone through Spring's `@PostConstruct` load step, so it wasn't testing what I thought it was — worth having a look at `SessionServiceTest` if you want the full story.

**Cancellation is a real conversation flow, not just an FAQ answer.** `CANCEL_APPOINTMENT` is now its own intent: the bot asks for the date/time, looks up the actual calendar event, confirms before touching anything, then deletes the event and emails the patient using the attendee address that's already on the event. One tradeoff worth flagging: the brief's own FAQ list says cancellations should be handled by messaging the clinic directly, and I removed that FAQ entry so the real flow could take over — a deliberate call to actually build the feature rather than leave the canned non-answer in place, but it does mean the bot's behavior no longer matches that one line of the brief.

## Env vars

| Variable | What it's for |
|---|---|
| `TELEGRAM_BOT_USERNAME` | bot's @username from BotFather |
| `TELEGRAM_BOT_TOKEN` | bot's token from BotFather |
| `GEMINI_API_KEY` | Gemini API key |
| `GOOGLE_CREDENTIALS_PATH` | path to the OAuth client JSON, e.g. `config/credentials.json` |
| `EMAIL_USERNAME` | Gmail address (SMTP login + from-address) |
| `EMAIL_PASSWORD` | Gmail App Password — not your real password |

## Layout

Packages are feature-oriented rather than one big `service`/`controller` split — each feature owns its own `service`/`model`/`config`/`exception` as needed:

```
telegram/     bot wiring - receives/sends messages, nothing else
agent/        AgentOrchestratorService, the conversation state machine
ai/           Gemini integration
booking/      domain model + workflow, glues calendar + email together
calendar/     Google Calendar integration
email/        SMTP + HTML templates
faq/          local FAQ matcher
conversation/ per-chat session + state
config/       cross-cutting settings (clinic name, timezone)
exception/    global REST error handling
```

## Assumptions I made

- One appointment type, fixed 30-minute slots — no per-doctor/per-resource scheduling.
- Telegram chat ID is the only identity there is; no login, no multi-clinic support.
- "Business day" = Monday–Friday per the brief, with no holiday calendar on top of that.
- Only the primary Google Calendar is used.
- One conversation per chat ID at a time — rapid-fire messages from the same user aren't debounced.

## What I'd do next with more time

- **Deploy it somewhere.** Right now it only runs while my laptop's running it. Containerizing and putting it on something like Fly.io or Render would make it actually persistent.
- **A real reminder/notification path.** Nothing currently reaches out to the patient except the booking and cancellation emails — no day-before reminder, no way for the clinic side to push a message back into an existing conversation.
- **Move the JSON file store to something more concurrent-write-safe if this ever became multi-instance.** Fine as-is for one process; would need rethinking (Redis, a real DB) the moment there's more than one.
