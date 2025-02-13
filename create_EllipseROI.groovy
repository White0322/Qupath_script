import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import java.util.Random

int z = 0
int t = 0
def plane = ImagePlane.getPlane(z, t)

// 获取当前图像的数据，以确保随机位置在图像内
def imageData = getCurrentImageData()
def server = imageData.getServer()
def imageWidth = server.getWidth()
def imageHeight = server.getHeight()

//圆形ROI大小设置
def ellipseWidth = 2000
def ellipseHeight = 2000

Random random = new Random()

for (int i = 1; i <= 5; i++) {
// 随机生成椭圆形ROI的位置，确保椭圆形ROI完全在图像内部
    def centerX = random.nextInt(imageWidth - ellipseWidth) + ellipseWidth / 2
    def centerY = random.nextInt(imageHeight - ellipseHeight) + ellipseHeight / 2

    // 创建椭圆形 ROI
    def ellipseRoi = ROIs.createEllipseROI(centerX - ellipseWidth / 2, centerY - ellipseHeight / 2, ellipseWidth, ellipseHeight, plane)
    // 创建注释对象，并将椭圆形 ROI 添加到注释对象中
    def ellipseAnnotation = PathObjects.createAnnotationObject(ellipseRoi)
    // 设置注释对象的名称为纯数字
    ellipseAnnotation.setName((i).toString())
    // 添加注释信息
//    ellipseAnnotation.getMeasurementList().putMeasurement("Type", "Ellipse")

    // 添加椭圆形注释对象到当前图像
    addObject(ellipseAnnotation)
}
