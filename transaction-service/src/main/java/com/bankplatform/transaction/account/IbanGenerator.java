package com.bankplatform.transaction.account;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Generates Azerbaijani-format IBANs ({@code AZkk BBBB + 20 digits}, 28 chars) with valid
 * ISO 13616 / ISO 7064 MOD 97-10 check digits, using the fictional bank code {@value #BANK_CODE}.
 */
@Component
public class IbanGenerator {

    static final String COUNTRY = "AZ";
    static final String BANK_CODE = "BTPX";
    private static final int ACCOUNT_DIGITS = 20;
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder account = new StringBuilder(ACCOUNT_DIGITS);
        for (int i = 0; i < ACCOUNT_DIGITS; i++) {
            account.append(random.nextInt(10));
        }
        return build(account.toString());
    }

    /** Builds an IBAN for a given 20-character account part (used for deterministic demo data). */
    public static String build(String accountPart) {
        String bban = BANK_CODE + accountPart;
        int check = 98 - mod97(bban + COUNTRY + "00");
        return COUNTRY + "%02d".formatted(check) + bban;
    }

    public static boolean isValid(String iban) {
        if (iban == null || iban.length() < 15 || !iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]+")) {
            return false;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }

    private static int mod97(String value) {
        StringBuilder numeric = new StringBuilder();
        for (char c : value.toCharArray()) {
            numeric.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : c);
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue();
    }
}
