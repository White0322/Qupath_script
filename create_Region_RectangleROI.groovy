import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import java.util.Random
import java.awt.geom.Point2D  // 引入 Point2D 类

int z = 0
int t = 0
ImagePlane plane = ImagePlane.getPlane(z, t)

// 获取当前图像的数据
def imageData = getCurrentImageData()
def server = imageData.getServer()
int imageWidth = server.getWidth()
int imageHeight = server.getHeight()

// 假设您已经有一个区域对象 region（例如从所有注释区域中获取一个）
def region = getAnnotationObjects().get(0)  // 选择第一个注释区域
def regionRoi = region.getROI()  // 获取该区域的ROI

// 获取区域的边界
double regionX = regionRoi.getBoundsX()
double regionY = regionRoi.getBoundsY()
double regionWidth = regionRoi.getBoundsWidth()
double regionHeight = regionRoi.getBoundsHeight()

// 矩形ROI的大小设置
int roiWidth = 2000
int roiHeight = 2000

Random random = new Random()

// 存储已生成的ROI中心点，用于检查距离
List<Point2D.Double> roiCenters = []  // 使用 Point2D.Double 来代替 Point

// 定义最小距离阈值，确保ROI之间不会过于接近
int minDistance = 2000  // 可以根据需求调整此值

// 确保生成5个ROI
for (int i = 1; i <= 5; i++) {
    boolean validPosition = false
    int x = 0
    int y = 0

    // 尝试直到找到一个有效的位置
    while (!validPosition) {
        // 确保矩形ROI完全位于注释区域内
        // 生成位置时，确保ROI完全位于区域内部
        x = random.nextInt((int)(regionWidth - roiWidth)) + (int)regionX
        y = random.nextInt((int)(regionHeight - roiHeight)) + (int)regionY

        // 检查新生成的矩形是否完全在Region内部
        if (x >= regionX && x + roiWidth <= regionX + regionWidth &&
            y >= regionY && y + roiHeight <= regionY + regionHeight) {
            
            // 检查新生成的ROI与已有ROI的最小距离
            validPosition = true
            Point2D.Double newCenter = new Point2D.Double(x + roiWidth / 2, y + roiHeight / 2)  // 使用 Point2D.Double

            // 检查新ROI的中心点与已有ROI中心点之间的距离
            for (Point2D.Double center : roiCenters) {
                double distance = newCenter.distance(center)  // 计算中心点之间的距离
                if (distance < minDistance) {
                    validPosition = false  // 如果距离太近，则标记为无效位置，重新生成
                    break
                }
            }

            // 如果有效位置，添加新ROI的中心点到roiCenters列表
            if (validPosition) {
                roiCenters.add(newCenter)
            }
        }
    }

    // 创建矩形 ROI
    def rectRoi = ROIs.createRectangleROI(x, y, roiWidth, roiHeight, plane)
    // 创建注释对象，并将矩形 ROI 添加到注释对象中
    def rectAnnotation = PathObjects.createAnnotationObject(rectRoi)
    // 设置注释对象的名称为纯数字
    rectAnnotation.setName(i.toString())

    // 添加矩形注释对象到当前图像
    addObject(rectAnnotation)
}
