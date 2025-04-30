/**
 * Convert detected nuclei to cells and apply additional cell expansion.
 * Simplified script to focus only on cell expansion functionality.
 */ 

import qupath.lib.roi.RoiTools
import qupath.lib.roi.interfaces.ROI

// Get the current hierarchy
def hierarchy = getCurrentHierarchy()

// Convert nuclei to cells, with a fixed expansion (here 10 pixels)
def detections = getDetectionObjects().findAll { d -> !d.isCell() }
def cells = CellTools.detectionsToCells(detections, 10, -1)

// Add cells to the hierarchy and remove original detections
hierarchy.addObjects(cells)
hierarchy.removeObjects(detections, true)

// Apply additional cell expansion (e.g., 2 pixels)
double additionalExpansion = 2.0  // Adjust this value as needed

cells.each { cell ->
    // Get the current cell ROI (which includes the nucleus and initial expansion)
    ROI currentROI = cell.getROI()
    if (currentROI == null) {
        println("Cell object has no ROI, skipping: " + cell)
        return
    }

    // Expand the cell ROI further
    ROI expandedROI = RoiTools.buffer(currentROI, additionalExpansion)

    // Update the cell with the new expanded ROI
    cell.setROI(expandedROI)
}

// No need for explicit hierarchy update, as removeObjects(true) handles it
println("Done! Applied additional cell expansion of " + additionalExpansion + " pixels to " + cells.size() + " cells.")