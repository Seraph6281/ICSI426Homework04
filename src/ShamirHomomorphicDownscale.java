import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer; // For byte conversions
import java.nio.ByteOrder; // For Little Endian
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * SSS homomorphic image downscaling
 * @author Yingzhao (Seraph) Ma
 * @version 1.1
 */
public class ShamirHomomorphicDownscale {

    // SSS Constants and Methods
    private static final int K = 2;
    private static final int N = 3;
    private static final int PRIME = 257;
    private static final BigInteger BIG_PRIME = BigInteger.valueOf(PRIME);
    private static final int HEADER_SIZE = 54;
    private static final SecureRandom random = new SecureRandom();
    private static final int INV_4 = 193; // Pre-calculated Modular Inverse of 4 mod 257

    // Finite Field Arithmetic (GF(PRIME))
    private static int modAdd(int a, int b) { return (a + b) % PRIME; }
    private static int modSub(int a, int b) { return (a - b + PRIME) % PRIME; }
    private static int modMul(int a, int b) { return (int) (((long) a * b) % PRIME); }
    private static int modInverse(int n) {
        if (n == 0) throw new IllegalArgumentException("Inverse of 0");
        return BigInteger.valueOf(n).modInverse(BIG_PRIME).intValue();
    }
    private static int evaluatePolynomial(int secret, int coeff, int x) {
        return modAdd(secret, modMul(coeff, x));
    }

    // SSS Share Creation/Reconstruction method
    public static void createShares(String inputImagePath, String outputPrefix) throws IOException { /* ... Same as before ... */
        byte[] imageBytes = Files.readAllBytes(Paths.get(inputImagePath));
        if (imageBytes.length < HEADER_SIZE) throw new IllegalArgumentException("Input too small.");
        byte[] header = Arrays.copyOfRange(imageBytes, 0, HEADER_SIZE);
        byte[] data = Arrays.copyOfRange(imageBytes, HEADER_SIZE, imageBytes.length);
        byte[][] shareData = new byte[N][data.length];
        int[] shareXValues = {1, 2, 3}; // x=1, 2, 3
        for (int i = 0; i < data.length; i++) {
            int secret = data[i] & 0xFF;
            int a1 = random.nextInt(PRIME);
            for (int j = 0; j < N; j++) {
                shareData[j][i] = (byte) evaluatePolynomial(secret, a1, shareXValues[j]);
            }
        }
        for (int j = 0; j < N; j++) {
            Path sharePath = Paths.get(outputPrefix + "_" + shareXValues[j] + ".bmp");
            writeBMP(sharePath, header, shareData[j]);
        }
    }

    /**
     * Image reconstruction method
     * @param sharePaths path of shares
     * @param shareXValues values of X values of shares
     * @param outputImagePath path of the output image
     * @throws IOException if an I/O error occurs
     */
    public static void reconstructImage(String[] sharePaths, int[] shareXValues, String outputImagePath) throws IOException { /* ... Same as before ... */
        if (sharePaths.length < K || shareXValues.length < K || sharePaths.length != shareXValues.length)
            throw new IllegalArgumentException("Invalid shares provided.");
        String[] pathsToUse = Arrays.copyOfRange(sharePaths, 0, K);
        int[] xValuesToUse = Arrays.copyOfRange(shareXValues, 0, K);
        byte[][] shareData = new byte[K][];
        byte[] header = null;
        int dataLength = -1;
        for (int i = 0; i < K; i++) {
            byte[] shareBytes = Files.readAllBytes(Paths.get(pathsToUse[i]));
            if (i == 0) {
                if (shareBytes.length < HEADER_SIZE) throw new IllegalArgumentException("Share file too small.");
                header = Arrays.copyOfRange(shareBytes, 0, HEADER_SIZE);
                dataLength = shareBytes.length - HEADER_SIZE;
                shareData[i] = Arrays.copyOfRange(shareBytes, HEADER_SIZE, shareBytes.length);
            } else {
                if ((shareBytes.length - HEADER_SIZE) != dataLength) throw new IllegalArgumentException("Share data lengths mismatch.");
                shareData[i] = Arrays.copyOfRange(shareBytes, HEADER_SIZE, shareBytes.length);
            }
        }
        byte[] reconstructedData = new byte[dataLength];
        for (int byteIndex = 0; byteIndex < dataLength; byteIndex++) {
            int[] currentX = new int[K]; int[] currentY = new int[K];
            for (int shareIndex = 0; shareIndex < K; shareIndex++) {
                currentX[shareIndex] = xValuesToUse[shareIndex];
                currentY[shareIndex] = shareData[shareIndex][byteIndex] & 0xFF;
            }
            int reconstructedSecret = 0;
            for (int i = 0; i < K; i++) {
                BigInteger num = BigInteger.ONE; BigInteger den = BigInteger.ONE;
                for (int j = 0; j < K; j++) {
                    if (i != j) {
                        num = num.multiply(BigInteger.valueOf(currentX[j]));
                        den = den.multiply(BigInteger.valueOf(modSub(currentX[j], currentX[i])));
                    }
                }
                int basis = num.multiply(den.modInverse(BIG_PRIME)).mod(BIG_PRIME).intValue();
                reconstructedSecret = modAdd(reconstructedSecret, modMul(currentY[i], basis));
            }
            if (reconstructedSecret > 255) {
                System.err.println("Warning: Clamping reconstructed value " + reconstructedSecret + " > 255 at index " + byteIndex);
                reconstructedSecret = 255;
            }
            reconstructedData[byteIndex] = (byte) reconstructedSecret;
        }
        writeBMP(Paths.get(outputImagePath), header, reconstructedData);
    }

    // BMP Header Helpers
    private static int readIntLE(byte[] buffer, int offset) {
        return ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static void writeIntLE(byte[] buffer, int offset, int value) {
        ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    private static void writeShortLE(byte[] buffer, int offset, short value) {
        ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value);
    }

    // Helper to write BMP file
    private static void writeBMP(Path path, byte[] header, byte[] data) throws IOException {
        byte[] fileBytes = new byte[header.length + data.length];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        System.arraycopy(data, 0, fileBytes, header.length, data.length);
        Files.write(path, fileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // Create a new BMP header for given dimensions (assuming 24-bit)
    private static byte[] createBMPHeader(int width, int height) {
        byte[] header = new byte[HEADER_SIZE];
        int bitsPerPixel = 24;
        int bytesPerPixel = bitsPerPixel / 8;
        // Row size must be multiple of 4 bytes (padding)
        int rowSize = ((width * bytesPerPixel) + 3) & ~3;
        int dataSize = rowSize * height;
        int fileSize = HEADER_SIZE + dataSize;

        // File Header (14 bytes)
        header[0] = 'B'; header[1] = 'M';              // Signature
        writeIntLE(header, 2, fileSize);                // File size
        // Bytes 6-9 reserved (0)
        writeIntLE(header, 10, HEADER_SIZE);            // Offset to pixel data

        // DIB Header (BITMAPINFOHEADER - 40 bytes)
        writeIntLE(header, 14, 40);               // DIB header size
        writeIntLE(header, 18, width);                  // Width
        writeIntLE(header, 22, height);                 // Height
        writeShortLE(header, 26, (short) 1);            // Color planes (1)
        writeShortLE(header, 28, (short) bitsPerPixel); // Bits per pixel
        writeIntLE(header, 30, 0);                // Compression (0 = BI_RGB)
        writeIntLE(header, 34, dataSize);               // Image data size
        writeIntLE(header, 38, 2835);             // Horizontal resolution (pixels/meter, ~72 DPI)
        writeIntLE(header, 42, 2835);             // Vertical resolution (pixels/meter, ~72 DPI)
        writeIntLE(header, 46, 0);                // Colors in palette (0 for 24-bit)
        writeIntLE(header, 50, 0);                // Important colors (0 = all)

        return header;
    }

    // Downscaling Functions

    /**
     * Downscales a regular BMP image by factor 2 using integer averaging.
     * @param inputPath path of input images
     * @param outputPath path of downscaled image
     * @throws IOException if an I/O error occurs
     */
    public static void downscaleImage(String inputPath, String outputPath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get(inputPath));
        if (imageBytes.length < HEADER_SIZE) throw new IllegalArgumentException("Input too small.");

        byte[] originalHeader = Arrays.copyOfRange(imageBytes, 0, HEADER_SIZE);
        int width = readIntLE(originalHeader, 18);
        int height = readIntLE(originalHeader, 22);
        short bpp = ByteBuffer.wrap(originalHeader, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();

        if (bpp != 24) throw new IllegalArgumentException("Only 24-bit BMPs are supported.");
        if (width % 2 != 0 || height % 2 != 0) throw new IllegalArgumentException("Image width and height must be even.");

        byte[] originalData = Arrays.copyOfRange(imageBytes, HEADER_SIZE, imageBytes.length);
        int bytesPerPixel = bpp / 8;
        int originalRowSize = ((width * bytesPerPixel) + 3) & ~3; // Padded row size

        int newWidth = width / 2;
        int newHeight = height / 2;
        byte[] newHeader = createBMPHeader(newWidth, newHeight); // Create header for new size
        int newRowSize = ((newWidth * bytesPerPixel) + 3) & ~3;
        byte[] newData = new byte[newRowSize * newHeight];

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int outPixelIndex = (y * newRowSize) + (x * bytesPerPixel);

                // Get 4 corresponding input pixel indices (top-left coordinates)
                int inX = x * 2;
                int inY = y * 2;

                // Calculate indices for the 4 pixels in the original data array
                int p1_idx = (inY * originalRowSize) + (inX * bytesPerPixel);       // (2x, 2y)
                int p2_idx = (inY * originalRowSize) + ((inX + 1) * bytesPerPixel); // (2x+1, 2y)
                int p3_idx = ((inY + 1) * originalRowSize) + (inX * bytesPerPixel); // (2x, 2y+1)
                int p4_idx = ((inY + 1) * originalRowSize) + ((inX + 1) * bytesPerPixel); // (2x+1, 2y+1)

                // Average each channel (B, G, R)
                for (int c = 0; c < bytesPerPixel; c++) {
                    int p1 = originalData[p1_idx + c] & 0xFF;
                    int p2 = originalData[p2_idx + c] & 0xFF;
                    int p3 = originalData[p3_idx + c] & 0xFF;
                    int p4 = originalData[p4_idx + c] & 0xFF;

                    // Integer average
                    int avg = (p1 + p2 + p3 + p4) / 4;
                    // Clamp just in case, though average shouldn't exceed 255
                    newData[outPixelIndex + c] = (byte) Math.max(0, Math.min(255, avg));
                }
            }
        }
        writeBMP(Paths.get(outputPath), newHeader, newData);
    }

    /**
     * Downscales a SHARE BMP image by factor 2 using finite field averaging.
     * @param inputPath path of image input
     * @param outputPath path of output downscaled images
     * @throws IOException if an I/O error occurs
     */
    public static void downscaleShareImage(String inputPath, String outputPath) throws IOException {
        byte[] shareBytes = Files.readAllBytes(Paths.get(inputPath));
        if (shareBytes.length < HEADER_SIZE) throw new IllegalArgumentException("Input too small.");

        byte[] originalHeader = Arrays.copyOfRange(shareBytes, 0, HEADER_SIZE);
        int width = readIntLE(originalHeader, 18);
        int height = readIntLE(originalHeader, 22);
        short bpp = ByteBuffer.wrap(originalHeader, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();

        // Shares should also be 24-bit structure, width/height must be even
        if (bpp != 24) throw new IllegalArgumentException("Shares expected to be 24-bit BMP structure.");
        if (width % 2 != 0 || height % 2 != 0) throw new IllegalArgumentException("Share width and height must be even.");

        byte[] originalShareData = Arrays.copyOfRange(shareBytes, HEADER_SIZE, shareBytes.length);
        int bytesPerPixel = bpp / 8;
        int originalRowSize = ((width * bytesPerPixel) + 3) & ~3; // Padded row size

        int newWidth = width / 2;
        int newHeight = height / 2;
        byte[] newHeader = createBMPHeader(newWidth, newHeight); // Create header for new size
        int newRowSize = ((newWidth * bytesPerPixel) + 3) & ~3;
        byte[] newShareData = new byte[newRowSize * newHeight];

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int outPixelIndex = (y * newRowSize) + (x * bytesPerPixel);

                // Get 4 corresponding input pixel indices (top-left coordinates)
                int inX = x * 2;
                int inY = y * 2;

                // Calculate indices for the 4 share pixels in the original data array
                int p1_idx = (inY * originalRowSize) + (inX * bytesPerPixel);       // (2x, 2y)
                int p2_idx = (inY * originalRowSize) + ((inX + 1) * bytesPerPixel); // (2x+1, 2y)
                int p3_idx = ((inY + 1) * originalRowSize) + (inX * bytesPerPixel); // (2x, 2y+1)
                int p4_idx = ((inY + 1) * originalRowSize) + ((inX + 1) * bytesPerPixel); // (2x+1, 2y+1)

                // Average each channel (B, G, R bytes representing share values) using GF(257)
                for (int c = 0; c < bytesPerPixel; c++) {
                    int y1 = originalShareData[p1_idx + c] & 0xFF;
                    int y2 = originalShareData[p2_idx + c] & 0xFF;
                    int y3 = originalShareData[p3_idx + c] & 0xFF;
                    int y4 = originalShareData[p4_idx + c] & 0xFF;

                    // Sum in GF(257)
                    int sum = modAdd(modAdd(y1, y2), modAdd(y3, y4));

                    // Multiply by inverse of 4 in GF(257)
                    int avg_share = modMul(sum, INV_4);

                    // Store the resulting share byte
                    // If avg_share is 256, it gets truncated to 0 here.
                    newShareData[outPixelIndex + c] = (byte) avg_share;
                }
            }
        }
        writeBMP(Paths.get(outputPath), newHeader, newShareData);
    }

    /**
     * Step 5: Calculates the Sum of Absolute Errors (SAE) between two images, as per the formula.
     * MAE = Sum(|I_o(j) - I_s(j)|)
     * @param imagePath1 path of the first image
     * @param imagePath2 path of the second image
     * @throws IOException if an I/O error occurs
     */
    public static long calculateSAE(String imagePath1, String imagePath2) throws IOException {
        byte[] bytes1 = Files.readAllBytes(Paths.get(imagePath1));
        byte[] bytes2 = Files.readAllBytes(Paths.get(imagePath2));

        if (bytes1.length < HEADER_SIZE || bytes2.length < HEADER_SIZE) {
            throw new IllegalArgumentException("One or both files too small.");
        }

        // Basic header check for dimensions
        int w1 = readIntLE(bytes1, 18); int h1 = readIntLE(bytes1, 22);
        int w2 = readIntLE(bytes2, 18); int h2 = readIntLE(bytes2, 22);
        if (w1 != w2 || h1 != h2) {
            throw new IllegalArgumentException(String.format(
                    "Image dimensions mismatch: %dx%d vs %dx%d", w1, h1, w2, h2));
        }
        if (bytes1.length != bytes2.length) {
            System.err.println("Warning: File sizes differ slightly, comparing data portion up to smaller size.");
            // This might happen if padding differs slightly, though createBMPHeader should prevent this.
        }

        byte[] data1 = Arrays.copyOfRange(bytes1, HEADER_SIZE, bytes1.length);
        byte[] data2 = Arrays.copyOfRange(bytes2, HEADER_SIZE, bytes2.length);

        long totalAbsoluteError = 0;
        int compareLength = Math.min(data1.length, data2.length); // Compare up to the length of the smaller data array

        for (int i = 0; i < compareLength; i++) {
            int val1 = data1[i] & 0xFF;
            int val2 = data2[i] & 0xFF;
            totalAbsoluteError += Math.abs(val1 - val2);
        }

        return totalAbsoluteError;
    }

    /**
     * Main entry point of the Shamir Homomorphic Downscaling
     * @param args the command line arguments.
     */
    public static void main(String[] args) {

        try (Scanner scanner = new Scanner(System.in)) {
            String inputImage = ""; Path inputPath;
            int width = 0, height = 0;

            // Get Valid Input Image Path (must have even dimensions)
            while (true) {
                System.out.print("Enter the full path to the input BMP image file (must have even width & height): ");
                inputImage = scanner.nextLine().trim();

                if (inputImage.isEmpty()) { System.out.println("Path cannot be empty."); continue; }

                inputPath = Paths.get(inputImage);

                if (Files.exists(inputPath) && Files.isRegularFile(inputPath)) {
                    try {
                        byte[] headerBytes = Files.readAllBytes(inputPath);
                        if(headerBytes.length < HEADER_SIZE) throw new IOException("File too small");
                        width = readIntLE(headerBytes, 18);
                        height = readIntLE(headerBytes, 22);
                        if (width % 2 != 0 || height % 2 != 0) {
                            System.out.printf("Error: Image dimensions (%d x %d) must be even.\n", width, height);
                            continue; // Ask for input again
                        }
                        short bpp = ByteBuffer.wrap(headerBytes, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                        if (bpp != 24) {
                            System.out.println("Error: Only 24-bit BMP files are supported.");
                            continue;
                        }
                    } catch (IOException e) {
                        System.out.println("Error reading image header: " + e.getMessage());
                        continue;
                    }
                    break; // Valid path and dimensions found
                } else {
                    System.out.println("Error: File not found or is not a regular file.");
                }
            }

            // Define output filenames
            String tempDir = "homomorphic_output";
            Files.createDirectories(Paths.get(tempDir)); // Create subdir for output

            String originalDownscaled = Paths.get(tempDir, "I_o_original_downscaled.bmp").toString();
            String sharePrefix = Paths.get(tempDir, "share_I").toString(); // Shares of original I
            String shareDownscaledPrefix = Paths.get(tempDir, "share_I_s").toString(); // Downscaled shares
            String reconstructedDownscaled = Paths.get(tempDir, "I_s_reconstructed_downscaled.bmp").toString();

            System.out.printf("\nInput Image: %s (%d x %d)\n", inputImage, width, height);
            System.out.println("Output will be placed in: " + tempDir + "/");

            // Step 1: Downscale Original Image
            System.out.println("\n--- Step 1: Taking an original image I of certain resolution and downscaling I -> I_o ---");
            downscaleImage(inputImage, originalDownscaled);
            System.out.println("Result saved as: " + originalDownscaled);

            // Step 2: Create Shares of Original Image
            System.out.println("\n--- Step 2: Creating shares of original image I -> I_1, I_2, I_3 ---");
            createShares(inputImage, sharePrefix);
            String share1 = sharePrefix + "_1.bmp";
            String share2 = sharePrefix + "_2.bmp";
            String share3 = sharePrefix + "_3.bmp";
            System.out.println("Shares saved as: " + share1 + ", " + share2 + ", " + share3);

            // Step 3: Downscale Shares
            System.out.println("\n--- Step 3: Downscaling shares I_k -> I_sk ---");
            String downShare1 = shareDownscaledPrefix + "_1.bmp";
            String downShare2 = shareDownscaledPrefix + "_2.bmp";
            String downShare3 = shareDownscaledPrefix + "_3.bmp";
            downscaleShareImage(share1, downShare1); System.out.println("Downscaled share 1 saved as: " + downShare1);
            downscaleShareImage(share2, downShare2); System.out.println("Downscaled share 2 saved as: " + downShare2);
            downscaleShareImage(share3, downShare3); System.out.println("Downscaled share 3 saved as: " + downShare3);

            // Step 4: Reconstruct from Downscaled Shares
            System.out.println("\n--- Step 4: Reconstructing from downscaled shares I_s1, I_s3 -> I_s ---");
            String[] sharesToRecon = {downShare1, downShare3};
            int[] xValsRecon = {1, 3}; // Using shares corresponding to x=1, x=3
            reconstructImage(sharesToRecon, xValsRecon, reconstructedDownscaled);
            System.out.println("Reconstructed downscaled image saved as: " + reconstructedDownscaled);

            // Step 5: Compute MAE (Sum Absolute Error)
            System.out.println("\n--- Step 5: Calculating Sum Absolute Error between I_o and I_s ---");
            long sae = calculateSAE(originalDownscaled, reconstructedDownscaled);
            System.out.println("Sum Absolute Error (SAE) = " + sae);

            if (sae == 0) {
                System.out.println("The Sum Absolute Error is 0.");
                System.out.println("This indicates perfect reconstruction *despite* the different averaging methods.");
                System.out.println("This outcome suggests that for this specific image and operation, the differences between integer division and finite field averaging, combined with any potential SSS clamping/truncation effects, cancelled out or did not occur in a way that affected the final byte values.");
                System.out.println("The additive homomorphic property of SSS holds perfectly under these conditions for the downscaling operation.");
            } else {
                int w_o = readIntLE(Files.readAllBytes(Paths.get(originalDownscaled)), 18);
                int h_o = readIntLE(Files.readAllBytes(Paths.get(originalDownscaled)), 22);
                long numBytes = (long)w_o * h_o * 3; // Approx number of data bytes (ignoring padding)
                double mae_per_byte = (double) sae / numBytes;
                System.out.printf("The Mean Absolute Error per byte (MAE) approx is %.4f).\n", mae_per_byte);

                // System.out.println("A non-zero SAE is expected due to several factors:");
                // System.out.println("  1. Different Averaging: Integer division in Step 1 (I->I_o) truncates differently than finite field multiplication by inv(4) in Step 3 (I_k->I_sk).");
                // System.out.println("  2. SSS Truncation/Clamping: If any intermediate SSS calculation (share creation, share averaging, final reconstruction) results in 256, it's truncated or clamped, introducing minor errors.");
                // System.out.println("The small error value demonstrates that the additive homomorphic property of SSS largely holds for the linear downscaling operation, with minor discrepancies arising from the interface between standard integer image processing and finite field arithmetic.");

            }

            System.out.println("\nCompare the images '" + originalDownscaled + "' and '" + reconstructedDownscaled + "' visually.");

        } catch (IOException e) {
            System.err.println("\nAn I/O error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.err.println("\nAn error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("\nAn unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}