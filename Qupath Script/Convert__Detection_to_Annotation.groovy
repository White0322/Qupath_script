def detections = getDetectionObjects()
def subset = detections.findAll {it.getROI()&& it.getPathClass() == getPathClass("Negative")}

def newAnnotations = subset.collect {
    return PathObjects.createAnnotationObject(it.getROI(), it.getPathClass(), it.getMeasurementList())
}
removeObjects(subset, true)
addObjects(newAnnotations)