package com.example.orderjdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Scanner;

public class OrderManagementApp {

    private static final String URL = "jdbc:mysql://localhost:3306/ecommerce_db";
    private static final String USER = "root";
    private static final String PASSWORD = "@NamanGupta07"; // Replace with your MySQL password
    private static final List<String> VALID_STATUSES = List.of("Pending", "Shipped", "Delivered");

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to ecommerce_db successfully.");

            while (true) {
                printMenu();
                int choice = readInt(scanner, "Enter your choice: ");

                switch (choice) {
                    case 1 -> insertOrders(conn, scanner);
                    case 2 -> viewOrdersByStatusAndQuantity(conn, scanner);
                    case 3 -> increaseQuantity(conn, scanner);
                    case 4 -> updateOrderStatus(conn, scanner);
                    case 5 -> showTotalOrderValue(conn);
                    case 6 -> deleteCancelledOrder(conn, scanner);
                    case 0 -> {
                        System.out.println("Exiting Order Management System.");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please select an option from 0 to 6.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database connection/SQL error: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Order Management System (JDBC) ===");
        System.out.println("1. Insert order records (at least 3) [1a]");
        System.out.println("2. View orders by status (quantity > 2, sorted by unit_price DESC) [1b]");
        System.out.println("3. Increase order quantity by order_id [1c]");
        System.out.println("4. Update order status (Pending / Shipped / Delivered) [1d]");
        System.out.println("5. Display total order value SUM(quantity * unit_price) [1e]");
        System.out.println("6. Delete order by order_id (only if status is Cancelled) [1e]");
        System.out.println("0. Exit");
    }

    // 1(a): Insert at least three order records using PreparedStatement; handle duplicate order_id
    private static void insertOrders(Connection conn, Scanner scanner) {
        int count = readInt(scanner, "How many orders do you want to insert? (enter at least 3): ");
        while (count < 3) {
            System.out.println("Please enter at least 3 orders to satisfy the requirement.");
            count = readInt(scanner, "How many orders do you want to insert? (enter at least 3): ");
        }

        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 1; i <= count; i++) {
                System.out.println("\n--- Enter details for Order #" + i + " ---");
                int orderId = readInt(scanner, "Order ID: ");
                String customerName = readLine(scanner, "Customer Name: ");
                String productName = readLine(scanner, "Product Name: ");
                int quantity = readInt(scanner, "Quantity: ");
                double unitPrice = readDouble(scanner, "Unit Price: ");
                String status = readLine(scanner, "Status (e.g., Pending, Shipped, Delivered, Cancelled): ");

                pstmt.setInt(1, orderId);
                pstmt.setString(2, customerName);
                pstmt.setString(3, productName);
                pstmt.setInt(4, quantity);
                pstmt.setDouble(5, unitPrice);
                pstmt.setString(6, status);

                try {
                    int rows = pstmt.executeUpdate();
                    if (rows > 0) {
                        System.out.println("Order " + orderId + " inserted.");
                    }
                } catch (SQLIntegrityConstraintViolationException dupEx) {
                    System.out.println("Duplicate order_id (" + orderId + ") skipped: an order with this ID already exists.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error inserting orders: " + e.getMessage());
        }
    }

    // 1(b): Accept status, display orders with status & quantity > 2, sorted by unit_price DESC
    private static void viewOrdersByStatusAndQuantity(Connection conn, Scanner scanner) {
        String status = readLine(scanner, "Enter order status to filter by: ");
        String sql = """
                SELECT order_id, customer_name, product_name, quantity, unit_price, status
                FROM Orders
                WHERE LOWER(status) = LOWER(?) AND quantity > 2
                ORDER BY unit_price DESC
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean found = false;
                System.out.println("\n--- Matching Orders (quantity > 2, sorted by unit_price DESC) ---");
                while (rs.next()) {
                    found = true;
                    printOrderRow(rs);
                }
                if (!found) {
                    System.out.println("No orders found with status '" + status + "' and quantity > 2.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching orders: " + e.getMessage());
        }
    }

    // 1(c): Accept order_id and additional quantity, increase existing quantity
    private static void increaseQuantity(Connection conn, Scanner scanner) {
        int orderId = readInt(scanner, "Enter Order ID: ");
        int addQty = readInt(scanner, "Enter additional quantity to add: ");

        if (addQty <= 0) {
            System.out.println("Additional quantity must be greater than 0.");
            return;
        }

        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, addQty);
            pstmt.setInt(2, orderId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Updated quantity for Order ID " + orderId + " by +" + addQty + ".");
            } else {
                System.out.println("Order with ID " + orderId + " does not exist.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating quantity: " + e.getMessage());
        }
    }

    // 1(d): Accept order_id and new status; validate (Pending, Shipped, Delivered) & display updated details
    private static void updateOrderStatus(Connection conn, Scanner scanner) {
        int orderId = readInt(scanner, "Enter Order ID: ");
        String rawStatus = readLine(scanner, "Enter new status (Pending, Shipped, Delivered): ");

        String matchedStatus = VALID_STATUSES.stream()
                .filter(s -> s.equalsIgnoreCase(rawStatus.trim()))
                .findFirst()
                .orElse(null);

        if (matchedStatus == null) {
            System.out.println("Invalid status '" + rawStatus + "'. Allowed statuses: Pending, Shipped, Delivered.");
            return;
        }

        String selectSql = "SELECT order_id, customer_name, product_name, quantity, unit_price, status FROM Orders WHERE order_id = ?";
        String updateSql = "UPDATE Orders SET status = ? WHERE order_id = ?";

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            selectStmt.setInt(1, orderId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Order with ID " + orderId + " does not exist.");
                    return;
                }
            }

            updateStmt.setString(1, matchedStatus);
            updateStmt.setInt(2, orderId);
            int updatedRows = updateStmt.executeUpdate();

            if (updatedRows > 0) {
                System.out.println("Status updated successfully. Updated order details:");
                selectStmt.setInt(1, orderId);
                try (ResultSet updatedRs = selectStmt.executeQuery()) {
                    if (updatedRs.next()) {
                        printOrderRow(updatedRs);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating order status: " + e.getMessage());
        }
    }

    // 1(e) part 1: Display total order value using SUM(quantity * unit_price)
    private static void showTotalOrderValue(Connection conn) {
        String sql = "SELECT COALESCE(SUM(quantity * unit_price), 0) AS total_value FROM Orders";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                double totalValue = rs.getDouble("total_value");
                System.out.printf("Total Order Value: %.2f%n", totalValue);
            }
        } catch (SQLException e) {
            System.out.println("Error calculating total order value: " + e.getMessage());
        }
    }

    // 1(e) part 2: Delete order only if its status is 'Cancelled'
    private static void deleteCancelledOrder(Connection conn, Scanner scanner) {
        int orderId = readInt(scanner, "Enter Order ID to delete: ");
        String checkSql = "SELECT status FROM Orders WHERE order_id = ?";
        String deleteSql = "DELETE FROM Orders WHERE order_id = ? AND status = 'Cancelled'";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

            checkStmt.setInt(1, orderId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Cannot delete: Order ID " + orderId + " does not exist.");
                    return;
                }
                String currentStatus = rs.getString("status");
                if (!"Cancelled".equalsIgnoreCase(currentStatus)) {
                    System.out.println("Cannot delete: Order ID " + orderId + " has status '" + currentStatus + "' (only 'Cancelled' orders can be deleted).");
                    return;
                }
            }

            deleteStmt.setInt(1, orderId);
            int rowsDeleted = deleteStmt.executeUpdate();
            if (rowsDeleted > 0) {
                System.out.println("Order ID " + orderId + " was Cancelled and has been deleted.");
            }
        } catch (SQLException e) {
            System.out.println("Error deleting order: " + e.getMessage());
        }
    }

    private static void printOrderRow(ResultSet rs) throws SQLException {
        System.out.printf("ID: %d | Customer: %s | Product: %s | Qty: %d | Unit Price: %.2f | Status: %s%n",
                rs.getInt("order_id"),
                rs.getString("customer_name"),
                rs.getString("product_name"),
                rs.getInt("quantity"),
                rs.getDouble("unit_price"),
                rs.getString("status"));
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}