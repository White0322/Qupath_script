/**
 * Add measurements to existing cell objects (nucleus, cytoplasm, and cell).
 */ 

import qupath.lib.analysis.features.ObjectMeasurements

// Get all cell objects
def cells = getCellObjects()
if (cells.isEmpty()) {
    println("No cell objects found!")
    return
}

// Add measurements (shape and intensity for nucleus, cytoplasm, and cell)
def imageData = getCurrentImageData()
def server = getServer(imageData)
clearCellMeasurements()
ObjectMeasurements.addShapeMeasurements(cells, server.getPixelCalibration())
double downsample = server.getDownsampleForResolution(0) // May want to compute at a different resolution!
def measurements = ObjectMeasurements.Measurements.values() as List
def compartments = ObjectMeasurements.Compartments.values() as List

cells.parallelStream().forEach { cell ->
    ObjectMeasurements.addIntensityMeasurements(server, cell, downsample, measurements, compartments)
}

// Refresh the hierarchy to reflect changes
fireHierarchyUpdate()
println("Done! Added measurements to " + cells.size() + " cells.")

/**
 * Get an ImageServer that applies color deconvolution, if needed
 */ 
def getServer(imageData) {
    def stains = imageData.getColorDeconvolutionStains()
    if (stains == null)
        return imageData.getServer()
    def builder = new qupath.lib.images.servers.TransformedServerBuilder(imageData.getServer())
    def stainNumbers = []
    for (int s = 1; s <= 3; s++) {
        if (!stains.getStain(s).isResidual())
            stainNumbers.add(s)
    }
    builder.deconvolveStains(stains, stainNumbers.stream().mapToInt(i -> i).toArray());
    return builder.build()
}