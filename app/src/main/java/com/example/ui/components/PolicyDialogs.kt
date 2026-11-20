package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.TrolleyTheme

enum class PolicyModalType {
    PRIVACY,
    TERMS,
}

@Composable
fun PolicyModalDialog(
    type: PolicyModalType,
    onDismiss: () -> Unit,
) {
    val isPrivacy = type == PolicyModalType.PRIVACY
    val closeInteraction = remember { MutableInteractionSource() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TROLLEY POLICIES · DRAFT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TrolleyTheme.colors.orange,
                )
                Text(
                    text = if (isPrivacy) "Privacy at Trolley" else "Trolley terms of service",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (isPrivacy) {
                        "This initial privacy notice describes the intended data principles. A jurisdiction-specific legal review and final policy are required before public launch."
                    } else {
                        "These draft terms outline the intended marketplace relationship. They are not final legal terms and require professional review before launch."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isPrivacy) {
                    PolicySection(
                        title = "Information and purpose",
                        body = "Trolley is designed to collect only information needed to create accounts, operate provider profiles, support enquiries and protect the marketplace. Account details, service enquiries and uploaded profile content are handled according to role-based access rules.",
                    )
                    PolicySection(
                        title = "Consent and analytics",
                        body = "Non-essential analytics and tracking must remain disabled until a user gives consent. Rejecting optional analytics must be as easy as accepting them. Essential application storage may still be used to provide core functionality.",
                    )
                    PolicySection(
                        title = "Security and access",
                        body = "Private records are protected by server-side authorization and database security rules. Users should only be able to access their own private data and conversations they participate in. Verification documents are private.",
                    )
                    PolicySection(
                        title = "Your choices & contact",
                        body = "Users may request access, correction, export, or deletion of their personal information where supported by applicable law. Reach our service team at gmrboy2009@gmail.com for privacy or account inquiries.",
                    )
                } else {
                    PolicySection(
                        title = "Marketplace role",
                        body = "Trolley helps customers discover independent service providers. Providers are responsible for the services, qualifications, quotations and work they offer. A directory listing is not a guarantee of service quality.",
                    )
                    PolicySection(
                        title = "Listings and reviews",
                        body = "Providers must supply accurate information and may only publish listings after required checks. Reviews must reflect genuine eligible interactions and may be moderated for abuse or policy violations.",
                    )
                    PolicySection(
                        title = "Quotations and payments",
                        body = "A quotation is not a confirmed booking or payment. Subscription prices, renewal dates, cancellation methods and applicable refund terms must be shown clearly before purchase. Payment status is confirmed by the payment provider, not a browser message.",
                    )
                    PolicySection(
                        title = "Safe use & support",
                        body = "Users must not impersonate others, submit fraudulent listings, harass other users or misuse personal information. For questions or safety reports, contact gmrboy2009@gmail.com.",
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                interactionSource = closeInteraction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .trolleySpringPress(closeInteraction)
                    .testTag("close_policy_dialog_button"),
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ConsentBannerCard(
    onReject: () -> Unit,
    onAccept: () -> Unit,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rejectInteraction = remember { MutableInteractionSource() }
    val acceptInteraction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.consent_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.consent_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onLearnMore,
                        modifier = Modifier.testTag("consent_learn_more_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.learn_more),
                            color = TrolleyTheme.colors.orange,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    interactionSource = rejectInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .weight(1f)
                        .trolleySpringPress(rejectInteraction)
                        .testTag("reject_consent_button"),
                ) {
                    Text(stringResource(R.string.reject_optional))
                }
                Button(
                    onClick = onAccept,
                    interactionSource = acceptInteraction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .trolleySpringPress(acceptInteraction)
                        .testTag("accept_consent_button"),
                ) {
                    Text(stringResource(R.string.accept_all))
                }
            }
        }
    }
}
