package com.brightcare_clinic.appointment_agent.web;

import com.brightcare_clinic.appointment_agent.config.ClinicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final ClinicProperties clinicProperties;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        String clinicName = clinicProperties.getName();

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      background: linear-gradient(135deg, #0F9D8C 0%%, #0B7A6D 100%%);
                      font-family: 'Segoe UI', Helvetica, Arial, sans-serif;
                    }
                    .card {
                      background: #FFFFFF;
                      border-radius: 16px;
                      padding: 48px 56px;
                      max-width: 420px;
                      text-align: center;
                      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
                    }
                    .badge {
                      display: inline-flex;
                      align-items: center;
                      gap: 8px;
                      background: #ECFDF5;
                      color: #0F9D8C;
                      font-size: 13px;
                      font-weight: 600;
                      padding: 6px 14px;
                      border-radius: 999px;
                      margin-bottom: 20px;
                    }
                    .dot {
                      width: 8px;
                      height: 8px;
                      border-radius: 50%%;
                      background: #22C55E;
                      animation: pulse 1.6s infinite;
                    }
                    @keyframes pulse {
                      0%% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.5); }
                      70%% { box-shadow: 0 0 0 8px rgba(34, 197, 94, 0); }
                      100%% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); }
                    }
                    h1 {
                      margin: 0 0 8px 0;
                      font-size: 26px;
                      color: #1F2933;
                    }
                    p {
                      margin: 0;
                      font-size: 14px;
                      line-height: 1.6;
                      color: #6B7280;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="badge"><span class="dot"></span>Service is running</div>
                    <h1>Welcome to %s</h1>
                    <p>The appointment booking agent is up and listening on Telegram.</p>
                  </div>
                </body>
                </html>
                """.formatted(clinicName, clinicName);
    }

}
