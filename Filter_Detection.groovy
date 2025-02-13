def value = 200
def feature_name = "Area µm^2"
def toDelete = getDetectionObjects().findAll{measurement(it,feature_name) <= value}
removeObjects(toDelete, true)