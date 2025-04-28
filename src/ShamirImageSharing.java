import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path; // Import Path
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList; // Import ArrayList
import java.util.Arrays;
import java.util.HashSet; // Import HashSet
import java.util.List;    // Import List
import java.util.Scanner; // Import Scanner
import java.util.Set;     // Import Set

/**
 * Shamir's Secret Sharing (SSS) scheme (both share preparation and reconstruction) for images
 * @author Yingzhao (Seraph) Ma
 * @version 1.1
 */
public class ShamirImageSharing {

    // Constants and Finite Field Methods (Keep exactly as before)
    private static final int K = 2; // Threshold number of shares needed
    private static final int N = 3; // Total number of shares generated
    private static final int PRIME = 257; // Prime modulus for GF(257)
    private static final BigInteger BIG_PRIME = BigInteger.valueOf(PRIME);
    private static final int HEADER_SIZE = 54; // Standard BMP header size
    private static final SecureRandom random = new SecureRandom();

    private static int modAdd(int a, int b) {
        return (a + b) % PRIME;
    }
    private static int modSub(int a, int b) {
        return (a - b + PRIME) % PRIME;
    }
    private static int modMul(int a, int b) {
        return (int) (((long) a * b) % PRIME);
    }
    private static int modInverse(int n) {
        if (n == 0) {
            throw new IllegalArgumentException("Cannot compute inverse of 0");
        }
        BigInteger bigN = BigInteger.valueOf(n);
        return bigN.modInverse(BIG_PRIME).intValue();
    }
    private static int evaluatePolynomial(int secret, int coeff, int x) {
        return modAdd(secret, modMul(coeff, x));
    }

    // SSS Core Logic
    public static void createShares(String inputImagePath, String outputPrefix) throws IOException {
        // 1. Read image bytes
        byte[] imageBytes = Files.readAllBytes(Paths.get(inputImagePath));
        if (imageBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Input file is too small to be a valid BMP with header.");
        }

        // 2. Separate header and data
        byte[] header = Arrays.copyOfRange(imageBytes, 0, HEADER_SIZE);
        byte[] data = Arrays.copyOfRange(imageBytes, HEADER_SIZE, imageBytes.length);

        // 3. Initialize share data arrays
        byte[][] shareData = new byte[N][data.length];
        int[] shareXValues = new int[N];
        for (int i = 0; i < N; i++) shareXValues[i] = i + 1;

        // 4. Process each data byte
        for (int i = 0; i < data.length; i++) {
            int secret = data[i] & 0xFF;
            int a1 = random.nextInt(PRIME);
            for (int j = 0; j < N; j++) {
                int x = shareXValues[j];
                int shareValueY = evaluatePolynomial(secret, a1, x);
                shareData[j][i] = (byte) shareValueY; // Cast acknowledges potential 256->0 truncation
            }
        }

        // 5. Create and write share files
        for (int j = 0; j < N; j++) {
            byte[] shareFileBytes = new byte[HEADER_SIZE + data.length];
            System.arraycopy(header, 0, shareFileBytes, 0, HEADER_SIZE);
            System.arraycopy(shareData[j], 0, shareFileBytes, HEADER_SIZE, data.length);
            Path sharePath = Paths.get(outputPrefix + "_" + shareXValues[j] + ".bmp");
            Files.write(sharePath, shareFileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // System.out.println("Created share: " + sharePath); // Keep output minimal during creation loop
        }
    }

    /**
     * Image reconstruction method
     * @param sharePaths path of shares
     * @param shareXValues values of X values of shares
     * @param outputImagePath path of the output image
     * @throws IOException if an I/O error occurs
     */
    public static void reconstructImage(String[] sharePaths, int[] shareXValues, String outputImagePath) throws IOException {
        if (sharePaths.length < K || shareXValues.length < K) throw new IllegalArgumentException("Need at least K=" + K + " shares.");
        if (sharePaths.length != shareXValues.length) throw new IllegalArgumentException("Paths and x-values count mismatch.");
        String[] pathsToUse = Arrays.copyOfRange(sharePaths, 0, K);
        int[] xValuesToUse = Arrays.copyOfRange(shareXValues, 0, K);
        byte[][] shareData = new byte[K][];
        byte[] header = null;
        int dataLength = -1;

        // 1. Read shares
        for (int i = 0; i < K; i++) {
            byte[] shareBytes = Files.readAllBytes(Paths.get(pathsToUse[i]));
            if (i == 0) {
                if (shareBytes.length < HEADER_SIZE) throw new IllegalArgumentException("Share file " + pathsToUse[i] + " too small.");
                header = Arrays.copyOfRange(shareBytes, 0, HEADER_SIZE);
                dataLength = shareBytes.length - HEADER_SIZE;
                shareData[i] = Arrays.copyOfRange(shareBytes, HEADER_SIZE, shareBytes.length);
            } else {
                if ((shareBytes.length - HEADER_SIZE) != dataLength) throw new IllegalArgumentException("Share files have inconsistent data lengths.");
                shareData[i] = Arrays.copyOfRange(shareBytes, HEADER_SIZE, shareBytes.length);
            }
        }
        // 2. Init reconstructed data
        byte[] reconstructedData = new byte[dataLength];

        // 3. Process bytes
        for (int byteIndex = 0; byteIndex < dataLength; byteIndex++) {
            int[] currentX = new int[K];
            int[] currentY = new int[K];
            for (int shareIndex = 0; shareIndex < K; shareIndex++) {
                currentX[shareIndex] = xValuesToUse[shareIndex];
                currentY[shareIndex] = shareData[shareIndex][byteIndex] & 0xFF;
            }

            // Lagrange Interpolation
            int reconstructedSecret = 0;
            for (int i = 0; i < K; i++) {
                BigInteger num = BigInteger.ONE;
                BigInteger den = BigInteger.ONE;
                for (int j = 0; j < K; j++) {
                    if (i != j) {
                        num = num.multiply(BigInteger.valueOf(currentX[j]));
                        den = den.multiply(BigInteger.valueOf(modSub(currentX[j], currentX[i])));
                    }
                }
                BigInteger fraction = num.multiply(den.modInverse(BIG_PRIME));
                int basis = fraction.mod(BIG_PRIME).intValue();
                reconstructedSecret = modAdd(reconstructedSecret, modMul(currentY[i], basis));
            }
            if (reconstructedSecret > 255) {
                System.err.println("Warning: Reconstructed secret " + reconstructedSecret + " > 255 at index " + byteIndex + ". Clamping.");
                reconstructedSecret = 255;
            }
            reconstructedData[byteIndex] = (byte) reconstructedSecret;
        }

        // 4. Create file bytes
        byte[] reconstructedFileBytes = new byte[HEADER_SIZE + dataLength];
        System.arraycopy(header, 0, reconstructedFileBytes, 0, HEADER_SIZE);
        System.arraycopy(reconstructedData, 0, reconstructedFileBytes, HEADER_SIZE, dataLength);

        // 5. Write file
        Path reconPath = Paths.get(outputImagePath);
        Files.write(reconPath, reconstructedFileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // System.out.println("Reconstructed image saved to: " + reconPath); // Keep output minimal here
    }

    /**
     * Main entry point of the Shamir’s Secret Sharing (SSS) scheme program
     * (both share preparation and reconstruction) for images
     * @param args The command line arguments.
     */
    public static void main(String[] args) {

        // Use try-with-resources to ensure scanner is closed automatically
        try (Scanner scanner = new Scanner(System.in)) {
            String inputImage = "";
            Path inputPath;

            // Get Valid Input Image Path
            while (true) {
                System.out.print("Enter the full path to the input BMP image file: ");
                inputImage = scanner.nextLine().trim(); // Read and trim whitespace

                if (inputImage.isEmpty()) {
                    System.out.println("Input path cannot be empty. Please try again.");
                    continue;
                }

                inputPath = Paths.get(inputImage);
                if (Files.exists(inputPath) && Files.isRegularFile(inputPath)) {
                    // Check extension (optional but helpful)
                    if (!inputImage.toLowerCase().endsWith(".bmp")) {
                        System.out.println("Warning: File does not end with .bmp. Ensure it is a valid 24-bit BMP file.");
                        // Decide if you want to force .bmp or allow proceeding: We'll allow it.
                    }
                    break; // Valid file found, exit loop
                } else {
                    System.out.println("Error: File not found or is not a regular file at the specified path.");
                    System.out.println("Please check the path and try again: " + inputImage);
                }
            }

            // Get Output Share Prefix
            String sharePrefix = "";
            while (sharePrefix.isEmpty()) {
                System.out.print("Enter a prefix for the output share files (e.g., 'share_out'): ");
                sharePrefix = scanner.nextLine().trim();
                if (sharePrefix.isEmpty()) {
                    System.out.println("Share prefix cannot be empty. Please try again.");
                }
                // You could add more validation here (e.g., check for invalid filename characters)
            }

            // Get Output Reconstructed Image Filename
            String reconstructedImage = "";
            while (reconstructedImage.isEmpty()) {
                System.out.print("Enter a filename for the reconstructed image (e.g., 'reconstructed.bmp'): ");
                reconstructedImage = scanner.nextLine().trim();
                if (reconstructedImage.isEmpty()) {
                    System.out.println("Reconstructed image filename cannot be empty. Please try again.");
                }
                // You could add more validation here
            }

            // Execute Share Creation
            try {
                System.out.println("\n--- Creating Shares ---");
                createShares(inputImage, sharePrefix); // Use user-provided input
                System.out.println("\nShares created successfully with prefix '" + sharePrefix + "'.");
                System.out.println("Share files generated:");
                for (int i = 1; i <= N; i++) {
                    System.out.println("  " + sharePrefix + "_" + i + ".bmp (Corresponds to x=" + i + ")");
                }
            } catch (IOException | IllegalArgumentException e) {
                System.err.println("\nError during share creation: " + e.getMessage());
                // e.printStackTrace(); // Optional: for detailed debug info
                return; // Exit if share creation fails
            }

            // Get Shares for Reconstruction
            List<Integer> chosenShareIndices = new ArrayList<>();
            List<String> sharesToReconstructList = new ArrayList<>();
            System.out.printf("\n--- Reconstruction Setup (Need %d shares) ---\n", K);

            while (chosenShareIndices.size() < K) {
                System.out.printf("Enter the numbers (x-values) of the %d shares you want to use\n", K);
                System.out.printf("(separated by spaces, e.g., '1 3' for shares %s_1.bmp and %s_3.bmp): ", sharePrefix, sharePrefix);

                String selection = scanner.nextLine().trim();
                String[] parts = selection.split("\\s+"); // Split by one or more spaces

                if (parts.length != K) {
                    System.out.printf("Error: Please enter exactly %d share numbers.\n", K);
                    continue; // Ask again
                }

                Set<Integer> uniqueIndices = new HashSet<>(); // Use Set to easily check for duplicates
                boolean inputValid = true;
                List<Integer> currentAttemptIndices = new ArrayList<>();
                List<String> currentAttemptPaths = new ArrayList<>();

                try {
                    for (String part : parts) {
                        int index = Integer.parseInt(part); // Can throw NumberFormatException

                        if (index < 1 || index > N) {
                            System.out.printf("Error: Share number %d is invalid. Must be between 1 and %d.\n", index, N);
                            inputValid = false;
                            break; // Stop checking this input set
                        }

                        if (!uniqueIndices.add(index)) {
                            System.out.printf("Error: Share number %d was entered more than once. Please enter distinct numbers.\n", index);
                            inputValid = false;
                            break; // Stop checking this input set
                        }

                        // If valid and unique so far, add it
                        currentAttemptIndices.add(index);
                        currentAttemptPaths.add(sharePrefix + "_" + index + ".bmp");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: Invalid input. Please enter numbers only, separated by spaces.");
                    inputValid = false;
                }

                if (inputValid) {
                    // Successfully parsed K valid, distinct indices
                    chosenShareIndices = currentAttemptIndices;
                    sharesToReconstructList = currentAttemptPaths;
                    // Check if these files actually exist before proceeding (robustness)
                    boolean filesExist = true;
                    for(String path : sharesToReconstructList) {
                        if (!Files.exists(Paths.get(path))) {
                            System.out.println("Error: The share file " + path + " does not exist. Shares might not have been created correctly or the prefix is wrong.");
                            filesExist = false;
                            chosenShareIndices.clear(); // Reset to trigger loop again
                            sharesToReconstructList.clear();
                            break;
                        }
                    }
                    if (!filesExist) {
                        continue; // Ask for input again
                    }
                    // If files exist and input was valid, the loop condition (chosenShareIndices.size() < K) will become false
                }
                // If input was not valid, loop continues automatically as chosenShareIndices is not filled
            }

            // Convert lists to arrays required by reconstructImage method
            String[] sharesToReconstruct = sharesToReconstructList.toArray(new String[0]);
            int[] shareXValuesForRecon = chosenShareIndices.stream().mapToInt(Integer::intValue).toArray();

            // Execute Reconstruction
            try {
                System.out.println("\n--- Reconstructing Image ---");
                System.out.println("Using shares: " + Arrays.toString(sharesToReconstruct));
                System.out.println("Corresponding x-values: " + Arrays.toString(shareXValuesForRecon));

                reconstructImage(sharesToReconstruct, shareXValuesForRecon, reconstructedImage); // Use user-provided output name

                System.out.println("\nReconstruction complete!");
                System.out.println("Reconstructed image saved as: " + reconstructedImage);
                System.out.println("\n--- Process Finished ---");

            } catch (IOException | IllegalArgumentException e) {
                System.err.println("\nError during reconstruction: " + e.getMessage());
                // e.printStackTrace(); // Optional: for detailed debug info
            } catch (Exception e) { // Catch unexpected errors
                System.err.println("\nAn unexpected error occurred during reconstruction: " + e.getMessage());
                e.printStackTrace();
            }

        } // Scanner is automatically closed here (due to try-with-resources)
    }
}