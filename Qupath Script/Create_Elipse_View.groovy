import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import java.util.Random

// 获取当前视图
def viewer = getCurrentViewer()
if (viewer == null) {
    println "没有找到当前视图！"
    return
}

// 获取当前显示的图像区域
def regionShape = viewer.getDisplayedRegionShape()
if (regionShape == null) {
    println "无法获取当前视图范围！"
    return
}

// 获取视图范围的边界
def bounds = regionShape.getBounds()
def viewX = bounds.x as int  // 转换为整数
def viewY = bounds.y as int  // 转换为整数
def viewWidth = bounds.width as int  // 转换为整数
def viewHeight = bounds.height as int  // 转换为整数

// 获取当前图像的 Z 和 T 层
def z = viewer.getZPosition()
def t = viewer.getTPosition()
def plane = ImagePlane.getPlane(z, t)

// 设置ROI尺寸
int ellipseWidth = 3000
int ellipseHeight = 3000

// 检查视图范围是否足够
if (viewWidth < ellipseWidth || viewHeight < ellipseHeight) {
    println "当前视图范围不足以创建ROI（需要至少 ${ellipseWidth}x${ellipseHeight} 像素）"
    return
}

Random random = new Random()

// 创建5个随机ROI
(1..5).each { i ->
    // 在可见范围内生成随机位置（确保ROI完全在视图中）
    int x = viewX + random.nextInt(viewWidth - ellipseWidth)
    int y = viewY + random.nextInt(viewHeight - ellipseHeight)
    
    // 创建椭圆形ROI（使用左上角坐标+宽高格式）
    def roi = ROIs.createEllipseROI(x, y, ellipseWidth, ellipseHeight, plane)
    
    // 创建并添加注释对象
    def annotation = PathObjects.createAnnotationObject(roi)
    annotation.setName(i.toString())
    addObject(annotation)
}

println "已在当前视图范围内创建5个随机ROI！"