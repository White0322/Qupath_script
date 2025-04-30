import qupath.imagej.tools.IJTools
import qupath.lib.gui.images.servers.RenderedImageServer
import qupath.lib.gui.viewer.overlays.HierarchyOverlay
import qupath.lib.regions.RegionRequest

import static qupath.lib.gui.scripting.QPEx.*


// It is important to define the downsample!
// This is required to determine annotation line thicknesses
double downsample = 5

//Write out each region corresponding to an unclassified annotation
//Only use this if you have created SMALL unclassified annotations!!

def imageName = GeneralTools.getNameWithoutExtension(getCurrentImageData().getServer().getMetadata().getName())
//Make sure the location you want to save the files to exists - requires a Project
def pathOutput_region_RGB = buildFilePath(PROJECT_BASE_DIR, 'region_RGB')
mkdirs(pathOutput_region_RGB)
def pathOutput_region_original = buildFilePath(PROJECT_BASE_DIR, 'region_original')
mkdirs(pathOutput_region_original)
// Request the current viewer for settings, and current image (which may be used in batch processing)
def viewer = getCurrentViewer()
def imageData = getCurrentImageData()
def server2 = getCurrentServer()

// def display = qupath.lib.display.ImageDisplay.create(imageData)
//Prior to version 0.5.0 use the following
//def display = new qupath.lib.display.ImageDisplay(imageData)

// Create a rendered server that includes a hierarchy overlay using the current display settings
// def server = new RenderedImageServer.Builder(imageData)
//     .display(display)
//     .downsamples(downsample)
//     .layers(new HierarchyOverlay(viewer.getImageRegionStore(), viewer.getOverlayOptions(), imageData))
//     .build()

// Create a rendered server that includes a hierarchy overlay using the current display settings
def server = new RenderedImageServer.Builder(imageData)
    .downsamples(downsample)
    .layers(new HierarchyOverlay(viewer.getImageRegionStore(), viewer.getOverlayOptions(), imageData))
    .build()
    
unclassifiedAnnotations = getAnnotationObjects().findAll{it.getPathClass() == null}

unclassifiedAnnotations.eachWithIndex{anno,x->
    //Name of the file and the path to where it goes in the Project
    def name = anno.getName()
    fileName = pathOutput_region_RGB+"//" + name + "-" + imageName+x+".png"
    fileName2 = pathOutput_region_original+"//" + name + "-" + imageName+x+".png"
    //For each annotation, we get its outline
    def roi = anno.getROI()
    //For each outline, we request the pixels within the bounding box of the annotation
    def requestROI = RegionRequest.createInstance(getCurrentServer().getPath(), 1, roi)
    //The 1 in the function above is the downsample, increase it for smaller images
    writeImageRegion(server, requestROI, fileName)
    writeImageRegion(server2, requestROI, fileName2)
}