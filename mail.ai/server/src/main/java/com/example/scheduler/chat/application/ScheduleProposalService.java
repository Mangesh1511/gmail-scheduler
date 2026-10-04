package com.example.scheduler.chat.application;

import com.example.scheduler.api.CreateFixedScheduleRequest;
import com.example.scheduler.api.CreateRecurrenceScheduleRequest;
import com.example.scheduler.chat.application.ChatAssistant.ProposedEmail;
import com.example.scheduler.chat.application.ChatAssistant.ProposedRecurrence;
import com.example.scheduler.chat.application.ChatAssistant.ProposedSchedule;
import com.example.scheduler.chat.domain.ScheduleProposal;
import com.example.scheduler.chat.persistence.ScheduleProposalStore;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

@Service
public class ScheduleProposalService {
    private static final Duration MAX_HORIZON = Duration.ofDays(7);
    private static final Duration MIN_LEAD_TIME = Duration.ofMinutes(2);
    private static final Duration PROPOSAL_TTL = Duration.ofMinutes(15);
    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_RECIPIENTS = 20;

    private final Validator validator;
    private final ScheduleProposalStore proposals;

    public ScheduleProposalService(Validator validator, ScheduleProposalStore proposals) {
        this.validator = validator;
        this.proposals = proposals;
    }

    public ScheduleProposal create(UUID conversationId, String ownerId, ProposedSchedule candidate) {
        if (candidate == null) throw invalid("The assistant did not provide schedule details.");
        List<ScheduleProposal.FixedEmail> emails = List.of();
        ScheduleProposal.RecurringEmail recurrence = null;
        if (candidate.recurrence() != null && (candidate.emails() == null || candidate.emails().isEmpty())) {
            recurrence = validateRecurrence(candidate.recurrence());
        } else if (candidate.recurrence() == null && candidate.emails() != null
                && !candidate.emails().isEmpty()) {
            if (candidate.emails().size() > MAX_BATCH_SIZE) {
                throw invalid("A request can include no more than " + MAX_BATCH_SIZE + " emails.");
            }
            emails = validateFixedBatch(candidate.emails());
        } else {
            throw invalid("The proposal must contain either fixed sends or one recurrence.");
        }

        return proposals.save(new ScheduleProposal(UUID.randomUUID(), conversationId, ownerId,
                ScheduleProposal.ProposalStatus.READY, emails, recurrence, Instant.now().plus(PROPOSAL_TTL), List.of()));
    }

    private List<ScheduleProposal.FixedEmail> validateFixedBatch(List<ProposedEmail> proposedEmails) {
        Instant now = Instant.now();
        List<ScheduleProposal.FixedEmail> emails = new ArrayList<>();
        for (int index = 0; index < proposedEmails.size(); index++) {
            var email = proposedEmails.get(index);
            if (email == null) throw invalid("Email " + (index + 1) + " is missing its details.");
            ZoneId timezone = parseTimezone(email.timezone());
            Instant sendAt = parseInstant(email.sendAt(), timezone);
            var request = new CreateFixedScheduleRequest(email.recipients(), email.subject(), email.body(), sendAt, timezone);
            String requestError = firstValidationMessage(request);
            if (requestError != null) {
                throw invalid("Email " + (index + 1) + ": " + requestError);
            }
            if (email.recipients().size() > MAX_RECIPIENTS)
                throw invalid("Email " + (index + 1) + " can have no more than " + MAX_RECIPIENTS + " recipients.");
            if (!sendAt.isAfter(now.plus(MIN_LEAD_TIME)))
                throw invalid("Email " + (index + 1) + " must be scheduled at least 2 minutes from now. Choose a later time.");
            if (sendAt.isAfter(now.plus(MAX_HORIZON)))
                throw invalid("Email " + (index + 1) + " must be scheduled within the next 7 days.");
            emails.add(new ScheduleProposal.FixedEmail(email.recipients(), email.subject(), email.body(), sendAt, timezone));
        }
        return List.copyOf(emails);
    }

    private ScheduleProposal.RecurringEmail validateRecurrence(ProposedRecurrence recurrence) {
        ZoneId timezone = parseTimezone(recurrence.timezone());
        Instant startAt = parseInstant(recurrence.startAt(), timezone);
        Instant endAt = parseInstant(recurrence.endAt(), timezone);
        var request = new CreateRecurrenceScheduleRequest(recurrence.recipients(), recurrence.subject(), recurrence.body(),
                startAt, endAt, recurrence.intervalSeconds(), timezone);
        long occurrences = recurrence.intervalSeconds() > 0
                ? Duration.between(startAt, endAt).getSeconds() / recurrence.intervalSeconds() + 1 : Long.MAX_VALUE;
        Instant now = Instant.now();
        String requestError = firstValidationMessage(request);
        if (requestError != null) throw invalid("Recurring email: " + requestError);
        if (recurrence.recipients().size() > MAX_RECIPIENTS)
            throw invalid("A recurring email can have no more than " + MAX_RECIPIENTS + " recipients.");
        if (!startAt.isAfter(now.plus(MIN_LEAD_TIME)))
            throw invalid("The first recurring email must be scheduled at least 2 minutes from now. Choose a later time.");
        if (endAt.isAfter(now.plus(MAX_HORIZON)))
            throw invalid("The recurrence must end within the next 7 days.");
        if (!endAt.isAfter(startAt)) throw invalid("The recurrence end time must be after its start time.");
        if (recurrence.intervalSeconds() < 3600)
            throw invalid("Recurring emails must be at least 1 hour apart.");
        if (occurrences > 168) throw invalid("A recurrence cannot include more than 168 emails.");
        return new ScheduleProposal.RecurringEmail(recurrence.recipients(), recurrence.subject(), recurrence.body(),
                startAt, endAt, recurrence.intervalSeconds(), timezone);
    }

    private static ZoneId parseTimezone(String value) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return ZoneId.of(value);
        } catch (RuntimeException exception) {
            throw invalid("The proposed timezone is invalid.");
        }
    }

    private static Instant parseInstant(String value, ZoneId timezone) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            // Bedrock can return a valid offset timestamp or local ISO date-time even when it omits the offset.
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException exception) {
            // Fall through to zoned and timezone-local formats.
        }
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (RuntimeException exception) {
            // Fall through to local date-time.
        }
        try {
            String localDateTime = value.strip().replaceFirst("^(\\d{4}-\\d{2}-\\d{2})\\s+", "$1T");
            String zoneSuffix = timezone.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            localDateTime = localDateTime.replaceFirst("\\s+" + java.util.regex.Pattern.quote(zoneSuffix) + "$", "");
            localDateTime = localDateTime.replaceFirst("\\s+" + java.util.regex.Pattern.quote(timezone.getId()) + "$", "");
            return LocalDateTime.parse(localDateTime).atZone(timezone).toInstant();
        } catch (RuntimeException exception) {
            throw invalid("I couldn't read the proposed send time. Please provide a future date and time.");
        }
    }

    private static InvalidScheduleProposalException invalid(String message) {
        return new InvalidScheduleProposalException(message);
    }

    private String firstValidationMessage(Object request) {
        return validator.validate(request).stream()
                .sorted(Comparator.comparing((jakarta.validation.ConstraintViolation<Object> violation) ->
                                violation.getPropertyPath().toString().startsWith("recipients") ? 0 : 1)
                        .thenComparing(violation -> violation.getPropertyPath().toString()))
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .findFirst().orElse(null);
    }
}
