package com.lis.astm.server.driver;

import com.lis.astm.model.AstmMessage;

/**
 * Interface for all instrument drivers.
 * Provides methods to parse incoming ASTM messages and build outgoing ASTM messages.
 * Each instrument driver implements this interface to handle instrument-specific variations.
 */
public interface InstrumentDriver {

    /**
     * Parse a raw ASTM message string into an AstmMessage object.
     * @param rawMessage the complete ASTM message string received from the instrument.
     * @return populated AstmMessage object.
     * @throws Exception if parsing encounters a critical error.
     */
    AstmMessage parse(String rawMessage) throws Exception;

    /**
     * Build an ASTM message string from an AstmMessage object.
     * @param message the AstmMessage object containing the data to transmit.
     * @return formatted ASTM message string ready for transmission.
     * @throws Exception if building encounters a critical error.
     */
    String build(AstmMessage message) throws Exception;

    /**
     * Get the name of the instrument this driver supports.
     * @return instrument name.
     */
    String getInstrumentName();

    /**
     * Get the version of the ASTM protocol this driver implements.
     * @return ASTM version (e.g., "1394-97").
     */
    String getAstmVersion();

    /**
     * Validate if a raw message is supported by this driver.
     * @param rawMessage the raw ASTM message.
     * @return true if the message can be processed by this driver.
     */
    boolean supportsMessage(String rawMessage);

    /**
     * Get driver-specific configuration or metadata.
     * @return driver configuration as a string (e.g., JSON).
     */
    String getDriverConfiguration();
}