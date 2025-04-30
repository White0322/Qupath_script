import qupath.lib.images.servers.PixelCalibration
import qupath.opencv.ml.pixel.PixelClassifiers
import qupath.opencv.ops.ImageOps
import java.nio.file.Path
import static qupath.lib.gui.scripting.QPEx.*

import qupath.lib.scripting.QP
import java.awt.image.ColorModel;

String pixelClassifierFolder = buildFilePath(PROJECT_BASE_DIR, "classifiers", "pixel_classifiers")
String newClassifierName="DoubleThresholdIntersection.json" //new classifier name. Must have .json ending.

double scale = 1.0 //0.5x scale from original image

def channelIndexes = [1,2]
def channelNames = ["520", "570"]
def channelThresholds = [20,30]

//define preprocessing and thresholds
def ops2 = [
        ImageOps.Channels.extract(*channelIndexes), //choose the channels for thresholding (0-based)
//        ImageOps.Filters.gaussianBlur(0), //1x gaussian smoothing
        ImageOps.Threshold.threshold(*channelThresholds), //threshold for each channel, matching the order of the extraction
        ImageOps.Core.multiply(1,2),
        ImageOps.Channels.sum()
]
def op = ImageOps.buildImageDataOp().appendOps(*ops2)

//get pixel calibration
imageData = getCurrentImageData()
PixelCalibration cal=imageData.getServer().getPixelCalibration()

def pixelValues = [1, 2, 3]
def classmap = [pixelValues, channelNames + channelNames.join("∩")].transpose().collectEntries()

newclassifier=PixelClassifiers.createClassifier(op,cal.createScaledInstance(scale,scale),classmap) //create the classifier

//write new classifier to file
Path writepath=Path.of(pixelClassifierFolder,newClassifierName)
PixelClassifiers.writeClassifier(newclassifier,writepath)

// Usually a good idea to print something, so we know it finished
print 'Done!'