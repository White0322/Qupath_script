import qupath.lib.gui.images.servers.RenderedImageServer
import qupath.lib.gui.viewer.overlays.HierarchyOverlay

double downsample = 10.0

def viewer = getCurrentViewer()
def channels = viewer.getImageDisplay().availableChannels();
def imageData = getCurrentImageData()

// Create a rendered server that includes a hierarchy overlay using the current display settings
def server = new RenderedImageServer.Builder(imageData)
    .downsamples(downsample)
    .layers(new HierarchyOverlay(viewer.getImageRegionStore(), viewer.getOverlayOptions(), imageData))
    .build()

// Add the output file path here
String path = buildFilePath(PROJECT_BASE_DIR, 'rendered_IHC_images', getProjectEntry().getImageName().replaceAll('\\.svs', '') + '.tiff')

// Write or display the rendered image
if (path != null) {
    mkdirs(new File(path).getParent())
    writeImage(server, path)
} else
    IJTools.convertToImagePlus(server, RegionRequest.createInstance(server)).getImage().show()

print 'Done!'