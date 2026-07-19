package com.brightcare_clinic.appointment_agent.conversation;

public enum ConversationState {

    GREETING,

    WAITING_FOR_BOOKING_DETAILS,

    WAITING_FOR_SLOT_CONFIRMATION,

    WAITING_FOR_NAME,

    WAITING_FOR_EMAIL,

    BOOKING_COMPLETED,

    WAITING_FOR_CANCELLATION_DETAILS,

    WAITING_FOR_CANCELLATION_CONFIRMATION

}
