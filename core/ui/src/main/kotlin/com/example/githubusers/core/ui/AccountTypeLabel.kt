package com.example.githubusers.core.ui

import com.example.githubusers.core.model.AccountType

/** アカウントの種類のラベル。GitHub の表記に合わせて英語のまま出す。 */
val AccountType.label: String
    get() = when (this) {
        AccountType.User -> "User"
        AccountType.Organization -> "Organization"
        AccountType.Bot -> "Bot"
        AccountType.Unknown -> "—"
    }
