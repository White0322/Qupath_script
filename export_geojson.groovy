// 检查注释对象是否存在图像中
def annotationObjects = getAnnotationObjects()
if (annotationObjects.size() > 0) {
    // 获取保存路径
    String path = buildFilePath(PROJECT_BASE_DIR, 'geojson', getProjectEntry().getImageName().replaceAll('\\.jpg', '') + '.geojson')

    if (path != null) {
        // 创建目录
        mkdirs(new File(path).getParent())

        // 写入图像，这可能需要根据你的需求进行更改
        // writeImage(server, path)

        // 导出对象到GeoJSON
        exportAllObjectsToGeoJson(path, "PRETTY_JSON", "FEATURE_COLLECTION")
        
        print 'Objects exported to GeoJSON!'
    } else {
        print 'Error: Unable to generate file path!'
    }
} else {
    print 'No objects to export to GeoJSON. Displaying image.'

}
