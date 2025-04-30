def shrinkUm = 0.5

def pixelSize = getCurrentServer().getPixelCalibration().getAveragedPixelSizeMicrons()


def detections = getDetectionObjects()

//def detections = getAnnotationObjects()

def nDetections = 0
def smallerDetections = detections.parallelStream().map{ detection -> 
    nDetections++
    if( nDetections % 500 == 0 ) println "Shrank ${nDetections} / ${detections.size()}"
    def geometry = detection.getROI().getGeometry().buffer( -1* shrinkUm / pixelSize )
    return PathObjects.createDetectionObject( GeometryTools.geometryToROI( geometry, detection.getROI().getImagePlane() ), detection.getPathClass() )
}.collect()  // 直接使用 collect()

removeObjects( detections, false )
addObjects( smallerDetections )

fireHierarchyUpdate()
