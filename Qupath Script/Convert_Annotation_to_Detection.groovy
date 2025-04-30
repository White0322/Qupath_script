def annotations = getAnnotationObjects()
def subset = annotations.findAll {it.getROI()&& it.getPathClass() == getPathClass("Tumor")}


def detections = subset.collect {
    PathObjects.createDetectionObject(it.getROI(), it.getPathClass(), it.getMeasurementList())
}

removeObjects(subset, true)
addObjects(detections)
