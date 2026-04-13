package rj.cocacode.nativets.yogalayout

data class Value(val unit: Int, val value: Double)

data class Layout(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val border: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    val padding: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    val margin: List<Double> = listOf(0.0, 0.0, 0.0, 0.0)
)

data class YogaCounters(
    val visited: Int = 0,
    val measured: Int = 0,
    val cacheHits: Int = 0,
    val live: Int = 0
)

typealias MeasureFunction = (width: Double, widthMode: Int, height: Double, heightMode: Int) -> Pair<Double, Double>

class Config {
    private var pointScaleFactor: Double = 1.0
    private var errata: Int = Errata.None
    private var useWebDefaults: Boolean = false
    
    fun free() {}
    
    fun isExperimentalFeatureEnabled(feature: Int): Boolean = false
    fun setExperimentalFeatureEnabled(feature: Int, enabled: Boolean) {}
    
    fun getPointScaleFactor(): Double = pointScaleFactor
    fun setPointScaleFactor(factor: Double) { pointScaleFactor = factor }
    fun getErrata(): Int = errata
    fun setErrata(e: Int) { errata = e }
    fun getUseWebDefaults(): Boolean = useWebDefaults
    fun setUseWebDefaults(v: Boolean) { useWebDefaults = v }
}

class Node(private val config: Config = Config()) {
    var style: MutableMap<String, Any?> = mutableMapOf()
    var layout: Layout = Layout()
    private var parentNodeRef: Node? = null
    var children: MutableList<Node> = mutableListOf()
    private var measureFuncRef: MeasureFunction? = null
    var isDirtyFlag: Boolean = true
    var isReferenceBaselineFlag: Boolean = false
    
    private var flexBasis: Double = 0.0
    private var mainSize: Double = 0.0
    private var crossSize: Double = 0.0
    private var lineIndex: Int = 0
    
    fun insertChild(child: Node, index: Int) {
        child.parentNodeRef = this
        children.add(index, child)
        markDirty()
    }
    
    fun removeChild(child: Node) {
        val idx = children.indexOf(child)
        if (idx >= 0) {
            children.removeAt(idx)
            child.parentNodeRef = null
            markDirty()
        }
    }
    
    fun getChild(index: Int): Node = children[index]
    fun getChildCount(): Int = children.size
    fun getParentNode(): Node? = parentNodeRef
    
    fun free() {
        parentNodeRef = null
        children = mutableListOf()
        measureFuncRef = null
    }
    
    fun freeRecursive() {
        for (child in children) child.freeRecursive()
        free()
    }
    
    fun reset() {
        style = mutableMapOf()
        children = mutableListOf()
        parentNodeRef = null
        measureFuncRef = null
        isDirtyFlag = true
    }
    
    fun markDirty() {
        isDirtyFlag = true
        parentNodeRef?.let { if (!it.isDirtyFlag) it.markDirty() }
    }
    
    fun isDirty(): Boolean = isDirtyFlag
    fun hasNewLayout(): Boolean = true
    fun markLayoutSeen() {}
    
    fun setMeasureFunc(fn: MeasureFunction?) {
        measureFuncRef = fn
        markDirty()
    }
    
    fun unsetMeasureFunc() {
        measureFuncRef = null
        markDirty()
    }
    
    fun getComputedLeft(): Double = layout.left
    fun getComputedTop(): Double = layout.top
    fun getComputedWidth(): Double = layout.width
    fun getComputedHeight(): Double = layout.height
    
    fun getComputedRight(): Double {
        val p = parentNodeRef
        return p?.let { it.layout.width - layout.left - layout.width } ?: 0.0
    }
    
    fun getComputedBottom(): Double {
        val p = parentNodeRef
        return p?.let { it.layout.height - layout.top - layout.height } ?: 0.0
    }
    
    fun getComputedLayout(): Map<String, Double> = mapOf(
        "left" to layout.left,
        "top" to layout.top,
        "right" to getComputedRight(),
        "bottom" to getComputedBottom(),
        "width" to layout.width,
        "height" to layout.height
    )
    
    fun getComputedBorder(edge: Int): Double = layout.border.getOrElse(edge) { 0.0 }
    fun getComputedPadding(edge: Int): Double = layout.padding.getOrElse(edge) { 0.0 }
    fun getComputedMargin(edge: Int): Double = layout.margin.getOrElse(edge) { 0.0 }
    
    fun setWidth(v: Any?) { markDirty() }
    fun setWidthPercent(v: Double) { markDirty() }
    fun setWidthAuto() { markDirty() }
    fun setHeight(v: Any?) { markDirty() }
    fun setHeightPercent(v: Double) { markDirty() }
    fun setHeightAuto() { markDirty() }
    fun setMinWidth(v: Any?) { markDirty() }
    fun setMinWidthPercent(v: Double) { markDirty() }
    fun setMinHeight(v: Any?) { markDirty() }
    fun setMinHeightPercent(v: Double) { markDirty() }
    fun setMaxWidth(v: Any?) { markDirty() }
    fun setMaxWidthPercent(v: Double) { markDirty() }
    fun setMaxHeight(v: Any?) { markDirty() }
    fun setMaxHeightPercent(v: Double) { markDirty() }
    
    fun setFlexDirection(dir: Int) { markDirty() }
    fun setFlexGrow(v: Double?) { markDirty() }
    fun setFlexShrink(v: Double?) { markDirty() }
    fun setFlex(v: Double?) { markDirty() }
    fun setFlexBasis(v: Any?) { markDirty() }
    fun setFlexBasisPercent(v: Double) { markDirty() }
    fun setFlexBasisAuto() { markDirty() }
    fun setFlexWrap(wrap: Int) { markDirty() }
    
    fun setAlignItems(a: Int) { markDirty() }
    fun setAlignSelf(a: Int) { markDirty() }
    fun setAlignContent(a: Int) { markDirty() }
    fun setJustifyContent(j: Int) { markDirty() }
    
    fun setDisplay(d: Int) { markDirty() }
    fun getDisplay(): Int = Display.Flex
    fun setPositionType(t: Int) { markDirty() }
    fun setPosition(edge: Int, v: Any?) { markDirty() }
    fun setPositionPercent(edge: Int, v: Double) { markDirty() }
    fun setPositionAuto(edge: Int) { markDirty() }
    fun setOverflow(o: Int) { markDirty() }
    fun setDirection(d: Int) { markDirty() }
    fun setBoxSizing(s: Int) {}
    
    fun setMargin(edge: Int, v: Any?) { markDirty() }
    fun setMarginPercent(edge: Int, v: Double) { markDirty() }
    fun setMarginAuto(edge: Int) { markDirty() }
    fun setPadding(edge: Int, v: Any?) { markDirty() }
    fun setPaddingPercent(edge: Int, v: Double) { markDirty() }
    fun setBorder(edge: Int, v: Double?) { markDirty() }
    fun setGap(gutter: Int, v: Any?) { markDirty() }
    fun setGapPercent(gutter: Int, v: Double) { markDirty() }
    
    fun getFlexDirection(): Int = FlexDirection.Column
    fun getJustifyContent(): Int = Justify.FlexStart
    fun getAlignItems(): Int = Align.Stretch
    fun getAlignSelf(): Int = Align.Auto
    fun getAlignContent(): Int = Align.FlexStart
    fun getFlexGrow(): Double = 0.0
    fun getFlexShrink(): Double = 0.0
    fun getFlexBasis(): Value? = null
    fun getFlexWrap(): Int = Wrap.NoWrap
    fun getWidth(): Value? = null
    fun getHeight(): Value? = null
    fun getOverflow(): Int = Overflow.Visible
    fun getPositionType(): Int = PositionType.Relative
    fun getDirection(): Int = Direction.Inherit
    
    fun copyStyle(other: Node) {}
    fun setDirtiedFunc(func: () -> Unit) {}
    fun unsetDirtiedFunc() {}
    fun setIsReferenceBaseline(v: Boolean) {
        isReferenceBaselineFlag = v
        markDirty()
    }
    fun isReferenceBaseline(): Boolean = isReferenceBaselineFlag
    fun setAspectRatio(ratio: Double?) {}
    fun getAspectRatio(): Double = Double.NaN
    fun setAlwaysFormsContainingBlock(v: Boolean) {}
    
    fun calculateLayout(ownerWidth: Double? = null, ownerHeight: Double? = null, direction: Int = Direction.LTR) {
    }
}

fun createConfig(): Config = Config()

fun getYogaCounters(): YogaCounters = YogaCounters()