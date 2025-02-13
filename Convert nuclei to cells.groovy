/**
 * Convert detected nuclei to cells and add measurements.
 * Written for v0.3.x to answer 
 * https://forum.image.sc/t/is-it-possible-to-modify-the-cell-expansion-parameter-after-cells-have-been-detected/61665
 * 
 * Because adding intensity measurements is especially awkward, this should be improved in a future version.
 *
 * Note that the cell measurements are *not* equivalent to QuPath's built-in cell detection.
 * They are calculated in a different way and have different names.
 * 
 * @author Pete Bankhead
 */ 

// Convert nuclei to cells, with a fixed expansion (here 10 pixels)
def detections = getDetectionObjects().findAll {d -> !d.isCell()}
def cells = CellTools.detectionsToCells(detections, 10, -1)
removeObjects(detections, true)
addObjects(cells)

// Add measurements (in v0.3 - currently awkward, may be changed/improved in a later version!)
import qupath.lib.analysis.features.ObjectMeasurements

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
fireHierarchyUpdate()


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