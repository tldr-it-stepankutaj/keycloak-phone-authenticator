package com.tegadeti.keycloak.provider;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.regex.Pattern;

/**
 * Phone number helpers (normalization, validation, masking).
 */
public final class PhoneNumberUtils {

    /** Calling code (without +) used for numbers entered without an international prefix. */
    public static final String DEFAULT_CALLING_CODE = "420";

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern CALLING_CODE = Pattern.compile("^[1-9]\\d{0,2}$");

    private static final PhoneNumberUtil LIB = PhoneNumberUtil.getInstance();

    private PhoneNumberUtils() {
    }

    /**
     * Normalizes user input into (hopefully) E.164 format.
     * <ol>
     *   <li>strips everything except digits and a leading +</li>
     *   <li>a leading + is kept as is, "00..." is treated as an international prefix</li>
     *   <li>otherwise libphonenumber parses the input as a national number of the default calling code's
     *       region (handles trunk prefixes correctly, e.g. the 0 of UK numbers vs. the 0 of Italian ones);
     *       the result is used only if it is a <em>valid</em> number</li>
     *   <li>fallback heuristics (also for test numbers libphonenumber does not know): a leading "0" on a
     *       10-digit number is a trunk prefix, a 9-digit number gets the default calling code</li>
     * </ol>
     */
    public static String normalize(String input, String defaultCallingCode) {
        if (input == null) {
            return null;
        }

        String cc = sanitizeCallingCode(defaultCallingCode);
        String normalized = input.replaceAll("[^+\\d]", "");

        if (normalized.startsWith("+")) {
            return normalized;
        }
        if (normalized.startsWith("00") && normalized.length() > 2) {
            return "+" + normalized.substring(2);
        }

        String viaLibrary = normalizeWithLibrary(normalized, cc);
        if (viaLibrary != null) {
            return viaLibrary;
        }

        if (normalized.startsWith("0") && normalized.length() == 10) {
            return "+" + cc + normalized.substring(1);
        }
        if (normalized.length() == 9) {
            return "+" + cc + normalized;
        }
        return normalized;
    }

    private static String normalizeWithLibrary(String nationalNumber, String callingCode) {
        try {
            String region = LIB.getRegionCodeForCountryCode(Integer.parseInt(callingCode));
            if (region == null || PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY.equals(region)) {
                return null;
            }
            Phonenumber.PhoneNumber parsed = LIB.parse(nationalNumber, region);
            if (LIB.isValidNumber(parsed)) {
                return LIB.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        } catch (NumberParseException | NumberFormatException e) {
            // fall through to heuristics
        }
        return null;
    }

    /** Basic E.164 shape check (+, no leading zero, at most 15 digits). */
    public static boolean isValid(String phoneNumber) {
        return isValid(phoneNumber, false);
    }

    /**
     * @param strict additionally require a number that is valid according to libphonenumber (country
     *               specific length and prefixes). Test numbers like +420123456789 fail in strict mode.
     */
    public static boolean isValid(String phoneNumber, boolean strict) {
        if (phoneNumber == null || !E164.matcher(phoneNumber).matches()) {
            return false;
        }
        if (!strict) {
            return true;
        }
        try {
            return LIB.isValidNumber(LIB.parse(phoneNumber, null));
        } catch (NumberParseException e) {
            return false;
        }
    }

    /** Masks a phone number for logs / UI: keeps the first four characters. */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    /** Returns a usable calling code (digits only, no +) or the default one if the input is malformed. */
    public static String sanitizeCallingCode(String callingCode) {
        if (callingCode == null) {
            return DEFAULT_CALLING_CODE;
        }
        String cleaned = callingCode.trim();
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        return CALLING_CODE.matcher(cleaned).matches() ? cleaned : DEFAULT_CALLING_CODE;
    }
}
