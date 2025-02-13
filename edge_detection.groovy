def annotations = getAnnotationObjects()

// Make a slightly smaller annotation and get all detections that are not fully inside of the annotation
annotations.each{ annot ->
    def touching = annot.getChildObjects()
                        .findAll{ !annot.getROI().getGeometry().buffer(-2).contains( it.getROI().getGeometry() ) }
    
    removeObjects( touching, false )
}

fireHierarchyUpdate()