package system;

import models.Admin;

import java.io.Console;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Enterprise System Provisioning and First-Run Bootstrapping Engine.
 * Detects uninitialized storage environments, guides system administrators through
 * primary cryptographic database master key generation and Super Admin account provisioning,
 * safely discloses disaster break-glass keys, and provides clean bindings for graphical setup wizards.
 */
public final class SystemBootstrap {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,24}$");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("^[0-9]{8,15}$");
    private static final Pattern CONTACT_PATTERN = Pattern.compile("^[+0-9\\s\\-]{8,20}$");

    /**
     * Immutable result wrapper containing initialized context upon successful bootstrapping.
     */
    public static final class BootstrapContext {
        private final ClinicMemory memory;
        private final char[] masterPassword;
        private final Admin rootAdmin;

        public BootstrapContext(ClinicMemory memory, char[] masterPassword, Admin rootAdmin) {
            this.memory = Objects.requireNonNull(memory);
            this.masterPassword = Objects.requireNonNull(masterPassword);
            this.rootAdmin = Objects.requireNonNull(rootAdmin);
        }

        public ClinicMemory getMemory() { return memory; }
        public char[] getMasterPassword() { return masterPassword; }
        public Admin getRootAdmin() { return rootAdmin; }
    }

    private SystemBootstrap() {
        // Utility class: enforce non-instantiability
    }

    /**
     * Determines whether the deployment environment requires initial bootstrapping.
     */
    public static boolean isBootstrapRequired(File databaseFile) {
        if (databaseFile == null || !databaseFile.exists() || databaseFile.length() == 0) {
            return true;
        }
        return false;
    }

    // =========================================================================
    // PROGRAMMATIC API (DIRECTLY CONSUMED BY GUI SETUP WIZARDS)
    // =========================================================================

    /**
     * Headless provisioning pipeline for Graphical Setup Wizards (JavaFX / Swing dialogs).
     */
    public static BootstrapContext provisionRootEnvironment(File targetDbFile,
                                                            char[] masterPassword,
                                                            String nationalId,
                                                            String fullName,
                                                            String contactNumber,
                                                            String username,
                                                            String rootPassword) throws Exception {

        Objects.requireNonNull(targetDbFile, "Database file target cannot be null.");
        Objects.requireNonNull(masterPassword, "Master encryption password cannot be null.");

        if (masterPassword.length < 8) {
            throw new IllegalArgumentException("Master encryption password must be at least 8 characters.");
        }

        ClinicMemory memory = new ClinicMemory();

        Admin rootAdmin = Admin.createRootAdmin(
                nationalId,
                fullName,
                contactNumber,
                username,
                rootPassword
        );

        memory.registerStaff(rootAdmin, "BOOTSTRAP_SYSTEM");
        memory.saveToEncryptedFile(targetDbFile, masterPassword);

        return new BootstrapContext(memory, masterPassword, rootAdmin);
    }

    // =========================================================================
    // INTERACTIVE CLI SETUP WIZARD (EXPERIENCE-OPTIMIZED CLI INTERFACE)
    // =========================================================================

    /**
     * Guides the user through a formatted, interactive first-run provisioning wizard.
     */
    public static BootstrapContext runInteractiveWizard(File targetDbFile, Scanner scanner) {
        Objects.requireNonNull(targetDbFile, "Target DB file cannot be null.");
        Objects.requireNonNull(scanner, "Scanner cannot be null.");

        printWizardHeader();

        // 1. Setup Master Encryption Key
        System.out.println("  [STEP 1/3] DATABASE CRYPTOGRAPHIC ENCRYPTION");
        System.out.println("  The hospital database requires an AES-256-GCM Master Key.");
        System.out.println("  This password encrypts all clinical records and financial data on disk.\n");

        char[] masterPassword = promptSecurePasswordWithConfirmation(scanner, "Enter Database Master Password: ", "Confirm Database Master Password: ");

        // 2. Setup Super Administrator Account
        System.out.println("\n  [STEP 2/3] ROOT SUPER ADMINISTRATOR PROVISIONING");
        System.out.println("  Create the primary executive administrator account with full system governance.\n");

        String fullName = promptNonEmpty(scanner, "Enter Root Admin Full Name: ");
        String nationalId = promptValidated(scanner, "Enter National ID: ", NATIONAL_ID_PATTERN, "National ID must be 8-15 digits.");
        String contactNumber = promptValidated(scanner, "Enter Contact Phone Number: ", CONTACT_PATTERN, "Invalid phone number format.");
        String username = promptValidated(scanner, "Enter Admin Username (4-24 alphanumeric): ", USERNAME_PATTERN, "Username must be 4-24 alphanumeric characters or underscores.");

        char[] adminPassword = promptSecurePasswordWithConfirmation(scanner, "Enter Admin Password: ", "Confirm Admin Password: ");

        // 3. Provision and Persist System
        System.out.println("\n  [STEP 3/3] COMMITTING INFRASTRUCTURE...");
        try {
            BootstrapContext context = provisionRootEnvironment(
                    targetDbFile,
                    masterPassword,
                    nationalId,
                    fullName,
                    contactNumber,
                    username,
                    new String(adminPassword)
            );

            Arrays.fill(adminPassword, '\0'); // Clear plain text password from memory

            printDisasterRecoveryKeyNotice(context.getRootAdmin().getRecoveryKey(), scanner);

            System.out.println("  >>> SYSTEM PROVISIONING COMPLETED SUCCESSFULLY. ENTERING OPERATIONAL MODE.\n");
            return context;

        } catch (Exception e) {
            System.err.println("\n  [CRITICAL ERROR] Failed to provision system: " + e.getMessage());
            throw new IllegalStateException("Bootstrap failed. Halting startup.", e);
        }
    }

    // =========================================================================
    // INTERNAL UI UTILITIES & INPUT VALIDATION
    // =========================================================================

    private static void printWizardHeader() {
        System.out.println("================================================================================");
        System.out.println("                      HOSPITAL SYSTEM FIRST-RUN WIZARD                          ");
        System.out.println("================================================================================");
        System.out.println("  Notice: No existing database detected. The system has initiated the automated ");
        System.out.println("  bootstrap pipeline to secure and configure the hospital deployment.           ");
        System.out.println("================================================================================\n");
    }

    private static void printDisasterRecoveryKeyNotice(String recoveryKey, Scanner scanner) {
        System.out.println("\n================================================================================");
        System.out.println("             CRITICAL NOTICE: EMERGENCY BREAK-GLASS RECOVERY KEY                ");
        System.out.println("================================================================================");
        System.out.println("  Your emergency break-glass recovery key is:                                  ");
        System.out.println();
        System.out.println("      " + recoveryKey);
        System.out.println();
        System.out.println("  Store this key in a secure physical location (e.g., safe deposit box).        ");
        System.out.println("  If all administrative accounts become locked due to brute-force attacks,      ");
        System.out.println("  this key is the ONLY mechanism to bypass system lockout without data loss.    ");
        System.out.println("================================================================================");

        System.out.print("  Type 'CONFIRM' to acknowledge you have recorded this key: ");
        while (true) {
            String input = scanner.nextLine().trim();
            if ("CONFIRM".equalsIgnoreCase(input)) {
                break;
            }
            System.out.print("  Please type 'CONFIRM' exactly to proceed: ");
        }
        System.out.println("================================================================================\n");
    }

    private static char[] readConsolePassword(Scanner scanner, String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("  %s", prompt);
        }
        // Fallback for IDE runners where System.console() is null
        System.out.print("  " + prompt);
        return scanner.nextLine().toCharArray();
    }

    private static char[] promptSecurePasswordWithConfirmation(Scanner scanner, String firstPrompt, String confirmPrompt) {
        while (true) {
            char[] pass1 = readConsolePassword(scanner, firstPrompt);
            if (pass1 == null || pass1.length < 8) {
                System.out.println("  [Error] Password must be at least 8 characters long.\n");
                continue;
            }

            char[] pass2 = readConsolePassword(scanner, confirmPrompt);
            if (!Arrays.equals(pass1, pass2)) {
                System.out.println("  [Error] Passwords do not match. Please try again.\n");
                Arrays.fill(pass1, '\0');
                Arrays.fill(pass2, '\0');
                continue;
            }

            Arrays.fill(pass2, '\0');
            return pass1;
        }
    }

    private static String promptNonEmpty(Scanner scanner, String prompt) {
        while (true) {
            System.out.print("  " + prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("  [Error] Field cannot be empty.");
        }
    }

    private static String promptValidated(Scanner scanner, String prompt, Pattern pattern, String errorMessage) {
        while (true) {
            System.out.print("  " + prompt);
            String input = scanner.nextLine().trim();
            if (pattern.matcher(input).matches()) {
                return input;
            }
            System.out.println("  [Error] " + errorMessage);
        }
    }
}