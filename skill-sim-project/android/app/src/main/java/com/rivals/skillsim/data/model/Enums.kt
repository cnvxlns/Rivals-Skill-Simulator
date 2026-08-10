package com.rivals.skillsim.data.model

import com.google.gson.annotations.SerializedName

enum class Tier {
    IRON,
    BRONZE,
    SILVER,
    GOLD,
    BLACK,
    MOMENT,
    HOF,
    WBC,
}

enum class Grade {
    D,
    C,
    B,
    A,
    S,
    S1,
    S2,
    S3,
    S4,
}

enum class TicketType {
    SKILL_CHANGE,
    PREMIUM_SKILL_CHANGE,
    SUPREME_SKILL_CHANGE,
}

enum class CardType {
    SIGNATURE,
    SIGNATURE_BLACK,
    WBC,
    WBC_SIGNATURE_BLACK,
    HOF,
    MOMENT,
    SUPREME_MOMENT,
}

enum class Position {
    PITCHER,
    BATTER,
}

enum class SubPosition {
    ALL,
    SP,
    RP,
    CP,
    C,
    @SerializedName("1B")
    FIRST_BASE,
    @SerializedName("2B")
    SECOND_BASE,
    @SerializedName("3B")
    THIRD_BASE,
    SS,
    LF,
    CF,
    RF,
    DH,
}
