import qupath.lib.geojson.*
import qupath.lib.objects.PathObjects
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.classes.PathClassFactory

def geojson_folder = 'E:/tes2/new_geojson/'
def folder = new File(geojson_folder)
def image_name = getProjectEntry().getImageName().replaceAll('\\.jpg', '')
folder.eachFile { file ->
    if (file.isFile()) {
        def file_name = file.name.replaceAll('\\.geojson', '')
        if (file_name == image_name) {
          def geojson = file
          def pathObjects = PathIO.readObjects(geojson)
          addObjects(pathObjects)
          println "The geojson file has been imported successfully: $image_name" 
       }
       }
}


