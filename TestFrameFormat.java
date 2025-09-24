import com.lis.astm.server.protocol.ChecksumUtils;

public class TestFrameFormat {
    public static void main(String[] args) {
        // Test frame creation with new CR format
        String testData = "H|\\^&|||OCD^VISION^5.14.0^Simulator|||||||P|LIS2-A|20220902174004";
        String frame = ChecksumUtils.buildFrame(1, testData, true);
        
        System.out.println("Generated Frame:");
        System.out.println("Raw: " + frame);
        
        // Display as hex for clarity
        System.out.println("Hex representation:");
        for (int i = 0; i < frame.length(); i++) {
            char c = frame.charAt(i);
            if (c == 0x02) System.out.print("<STX>");
            else if (c == 0x03) System.out.print("<ETX>");
            else if (c == 0x0D) System.out.print("<CR>");
            else if (c == 0x0A) System.out.print("<LF>");
            else if (c >= 32 && c <= 126) System.out.print(c);
            else System.out.printf("<%02X>", (int)c);
        }
        System.out.println();
        
        // Test validation
        boolean isValid = ChecksumUtils.validateFrameChecksum(frame);
        System.out.println("Frame validation: " + (isValid ? "PASS" : "FAIL"));
        
        // Extract data
        String extractedData = ChecksumUtils.extractFrameData(frame);
        System.out.println("Extracted data: " + extractedData);
        System.out.println("Data matches: " + testData.equals(extractedData));
    }
}
