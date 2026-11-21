
package edu.keiser.deadlock.banker;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;

public class BankersAlgorithm {


    private int[][] maxMatrix;
    private int[][] allocationMatrix;
    private int[] availableResources;

    private int numOfProcesses;
    private int numOfResources;

    private int[] safeSequence;

    public BankersAlgorithm(int numOfProcesses, int numOfResources) {
        if (numOfProcesses <= 0 || numOfResources <= 0) {
            throw new IllegalArgumentException(
                    "The number of processes and resources must be positive."
            );
        }

        this.numOfProcesses = numOfProcesses;
        this.numOfResources = numOfResources;

        maxMatrix = new int[numOfProcesses][numOfResources];
        allocationMatrix = new int[numOfProcesses][numOfResources];
        availableResources = new int[numOfResources];
    }

    // Receives the matrices from the GUI
    public void setData(
            int[][] max,
            int[][] allocation,
            int[] available
    ) {
        validateDimensions(max, allocation, available);

        for (int i = 0; i < numOfProcesses; i++) {
            for (int j = 0; j < numOfResources; j++) {
                if (max[i][j] < 0 || allocation[i][j] < 0) {
                    throw new IllegalArgumentException(
                            "Resource values cannot be negative."
                    );
                }

                if (allocation[i][j] > max[i][j]) {
                    throw new IllegalArgumentException(
                            "Allocation cannot be greater than Max at P"
                                    + i + ", R" + j + "."
                    );
                }
            }
        }

        for (int value : available) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "Available resources cannot be negative."
                );
            }
        }

        maxMatrix = copyMatrix(max);
        allocationMatrix = copyMatrix(allocation);
        availableResources = Arrays.copyOf(available, available.length);
        safeSequence = null;
    }

    private void validateDimensions(
            int[][] max,
            int[][] allocation,
            int[] available
    ) {
        if (max == null || allocation == null || available == null
                || max.length != numOfProcesses
                || allocation.length != numOfProcesses
                || available.length != numOfResources) {
            throw new IllegalArgumentException("Invalid matrix dimensions.");
        }

        for (int i = 0; i < numOfProcesses; i++) {
            if (max[i] == null || allocation[i] == null
                    || max[i].length != numOfResources
                    || allocation[i].length != numOfResources) {
                throw new IllegalArgumentException("Invalid matrix dimensions.");
            }
        }
    }

    // Reads Max, Allocation, and Available from a TXT file.
    // The file must contain: Max matrix, Allocation matrix, Available vector.
    public void readInputFromFile(String fileName)
            throws FileNotFoundException {

        try (Scanner scanner = new Scanner(new File(fileName))) {
            int[][] max = new int[numOfProcesses][numOfResources];
            int[][] allocation = new int[numOfProcesses][numOfResources];
            int[] available = new int[numOfResources];

            for (int i = 0; i < numOfProcesses; i++) {
                for (int j = 0; j < numOfResources; j++) {
                    if (!scanner.hasNextInt()) {
                        throw new IllegalArgumentException(
                                "The TXT file does not contain enough Max values."
                        );
                    }
                    max[i][j] = scanner.nextInt();
                }
            }

            for (int i = 0; i < numOfProcesses; i++) {
                for (int j = 0; j < numOfResources; j++) {
                    if (!scanner.hasNextInt()) {
                        throw new IllegalArgumentException(
                                "The TXT file does not contain enough Allocation values."
                        );
                    }
                    allocation[i][j] = scanner.nextInt();
                }
            }

            for (int j = 0; j < numOfResources; j++) {
                if (!scanner.hasNextInt()) {
                    throw new IllegalArgumentException(
                            "The TXT file does not contain enough Available values."
                    );
                }
                available[j] = scanner.nextInt();
            }

            if (scanner.hasNext()) {
                throw new IllegalArgumentException(
                        "The TXT file contains unexpected extra values."
                );
            }

            setData(max, allocation, available);
        }
    }

    public int[][] getNeedMatrix() {
        int[][] need = new int[numOfProcesses][numOfResources];

        for (int i = 0; i < numOfProcesses; i++) {
            for (int j = 0; j < numOfResources; j++) {
                need[i][j] = maxMatrix[i][j] - allocationMatrix[i][j];
            }
        }

        return need;
    }

    public int[][] getMaxMatrix() {
        return copyMatrix(maxMatrix);
    }

    public int[][] getAllocationMatrix() {
        return copyMatrix(allocationMatrix);
    }

    public int[] getAvailableResources() {
        return Arrays.copyOf(availableResources, availableResources.length);
    }

    public int[] getSafeSequence() {
        return safeSequence == null
                ? null
                : Arrays.copyOf(safeSequence, safeSequence.length);
    }

    public int getNumOfProcesses() {
        return numOfProcesses;
    }

    public int getNumOfResources() {
        return numOfResources;
    }

    // Banker's Algorithm safety check.
    public boolean isSafe() {
        int[][] need = getNeedMatrix();
        int[] work = Arrays.copyOf(availableResources, numOfResources);
        boolean[] finish = new boolean[numOfProcesses];

        int[] sequence = new int[numOfProcesses];
        int completed = 0;

        while (completed < numOfProcesses) {
            boolean foundProcess = false;

            for (int i = 0; i < numOfProcesses; i++) {
                if (finish[i]) {
                    continue;
                }

                boolean canFinish = true;

                for (int j = 0; j < numOfResources; j++) {
                    if (need[i][j] > work[j]) {
                        canFinish = false;
                        break;
                    }
                }

                if (canFinish) {
                    for (int j = 0; j < numOfResources; j++) {
                        work[j] += allocationMatrix[i][j];
                    }

                    finish[i] = true;
                    sequence[completed] = i;
                    completed++;
                    foundProcess = true;
                    break;
                }
            }

            if (!foundProcess) {
                safeSequence = null;
                return false;
            }
        }

        safeSequence = sequence;
        return true;
    }

    // Attempts to grant a resource request while preserving system safety.
    public boolean requestResources(int processId, int[] request) {
        if (processId < 0 || processId >= numOfProcesses
                || request == null || request.length != numOfResources) {
            return false;
        }

        int[][] need = getNeedMatrix();

        for (int j = 0; j < numOfResources; j++) {
            if (request[j] < 0
                    || request[j] > need[processId][j]
                    || request[j] > availableResources[j]) {
                return false;
            }
        }

        for (int j = 0; j < numOfResources; j++) {
            availableResources[j] -= request[j];
            allocationMatrix[processId][j] += request[j];
        }

        if (isSafe()) {
            return true;
        }

        // Roll back if the new state is unsafe.
        for (int j = 0; j < numOfResources; j++) {
            availableResources[j] += request[j];
            allocationMatrix[processId][j] -= request[j];
        }

        safeSequence = null;
        return false;
    }

    public void generateRandomData() {
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < numOfProcesses; i++) {
            for (int j = 0; j < numOfResources; j++) {
                maxMatrix[i][j] = random.nextInt(10);
                allocationMatrix[i][j] =
                        random.nextInt(maxMatrix[i][j] + 1);
            }
        }

        for (int j = 0; j < numOfResources; j++) {
            availableResources[j] = random.nextInt(10);
        }

        safeSequence = null;
    }

    private int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[source.length][];

        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }

        return copy;
    }
}
