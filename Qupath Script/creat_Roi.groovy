import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane

int z = 0
int t = 0
def plane = ImagePlane.getPlane(z, t)

// 定义矩形的位置和大小
def x = 450
def x1 = 550
def y = 450
def y1 = 550
def width = 700
def height = 700

// 创建矩形 ROI
def roi1 = ROIs.createRectangleROI(x, y, width, height, plane)
def roi2 = ROIs.createRectangleROI(x1, y1, width, height, plane)

// 创建注释对象，并将矩形 ROI 添加到注释对象中
def annotation = PathObjects.createAnnotationObject(roi1)
def annotation2 = PathObjects.createAnnotationObject(roi2)

// 将注释对象添加到当前图像
addObject(annotation)
addObject(annotation2)


