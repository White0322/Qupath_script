def classesToCount = ["Tumor","TILs","plasma cell"]
def classCount = [:]
for (className in classesToCount) {
   classCount[className] = 0 
}
for (annotation in getAnnotationObjects()) {
    def pathClass = annotation.getPathClass().getName()
    if (classCount.containsKey(pathClass)) {
       classCount[pathClass]++ 
    }
}
imageName = getProjectEntry().getImageName()
print("\n"+imageName)
for (className in classesToCount) {
   print(className +"：" + classCount[className]) 
}