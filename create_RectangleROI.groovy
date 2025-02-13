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

//矩形ROI大小设置
def roiWidth = 2000
def roiHeight = 2000

Random random = new Random()

//i<=需要创建几个ROI就设置为多少

for (int i = 1; i <= 5; i++) {
  // 确保矩形ROI完全在图像内部
    def x = random.nextInt(imageWidth - roiWidth)
    def y = random.nextInt(imageHeight - roiHeight)

    // 创建矩形 ROI
    def rectRoi = ROIs.createRectangleROI(x, y, roiWidth, roiHeight, plane)
    // 创建注释对象，并将矩形 ROI 添加到注释对象中
    def rectAnnotation = PathObjects.createAnnotationObject(rectRoi)
    // 设置注释对象的名称为纯数字
    rectAnnotation.setName(i.toString())
    // 添加注释信息
//    rectAnnotation.getMeasurementList().putMeasurement("Type", "Rectangle")

    // 添加矩形注释对象到当前图像
    addObject(rectAnnotation)

}
