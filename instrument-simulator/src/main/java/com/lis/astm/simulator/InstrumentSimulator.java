package com.lis.astm.simulator;

/**
 * ASTM E1381 Instrument Simulator
 * 
 * Main entry point for the modular ASTM instrument simulator.
 * This simulator provides comprehensive testing capabilities for ASTM Interface Services
 * including realistic protocol communication, message generation, and connection management.
 * 
 * Key Features:
 * - Modular architecture with separated concerns
 * - Realistic ASTM E1381 protocol implementation
 * - Comprehensive test data generation (CBC, Chemistry panels)
 * - Interactive menu system
 * - Keep-alive connection testing
 * - Configurable server settings
 * 
 * Architecture:
 * - SimulatorController: Main application coordination
 * - AstmProtocolHandler: Low-level protocol communication
 * - AstmMessageBuilder: ASTM message construction
 * - SampleDataGenerator: Test data generation
 * - SimulatorMenu: User interface management
 * 
 * @author Modular Refactoring - September 2025
 */
public class InstrumentSimulator {

    public static void main(String[] args) {
        try {
            SimulatorController controller = new SimulatorController();
            controller.run();
        } catch (Exception e) {
            System.err.println("Fatal error starting simulator: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
