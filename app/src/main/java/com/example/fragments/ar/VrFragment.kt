package com.example.fragments.ar

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.homesweethome.R
import com.example.fragments.shopping.ProductDetailsFragmentArgs
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TranslationController
import com.gorisse.thomas.sceneform.scene.await

class VrFragment : Fragment(R.layout.fragment_vr) {

    private val args by navArgs<ProductDetailsFragmentArgs>()

    private lateinit var arFragment: ArFragment

    private val arSceneView get() = arFragment.arSceneView
    private val scene get() = arSceneView.scene
    private var model: Renderable? = null
    private var modelView: ViewRenderable? = null
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var measuringLine: Node? = null
    private var knots: MutableList<Node> = mutableListOf()
    private val KNOT_COUNT = 10 // Adjust the count as needed
    private val LENGTH_IN_METERS = 3.0
    private var measuringInProgress = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val product = args.product
        val model = product.model

        arFragment = (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener { session, config ->
             }
            setOnViewCreatedListener { arSceneView ->
                arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
            }
            view.findViewById<Button>(R.id.ShowMeasuring).setOnClickListener {
                startMeasuring(LENGTH_IN_METERS.toFloat()) // Pass the desired length here
            }
            setOnTapArPlaneListener(::onTapPlane)
        }
        lifecycleScope.launchWhenCreated {
            loadModels(model)
        }

    }

    private fun startMeasuring(desiredLength: Float) {
        if (measuringInProgress) {
            // If measuring is in progress, hide measurements and show the product model
            clearMeasurements()
            showProductModel()
            measuringInProgress = false
        } else {
            // If measuring is not in progress, start measuring
            hideProductModel()
            createMeasuringLine(desiredLength)
            measuringInProgress = true
        }

    }

    private fun showProductModel() {
        model?.let { it.isShadowCaster = true }

    }
    private fun hideProductModel() {
        // Hide the product model here (you need to implement this based on your code)
        // For example, if your product model is a Renderable, you can set its visibility to false.
        model?.let { it.isShadowCaster = false }
    }
    private fun createMeasuringLine(desiredLength: Float) {
        clearMeasurements()

        // Create the measuring line
        val defaultKnotCount = 10 // Adjust the default count as needed
        val calculatedKnotCount = maxOf(defaultKnotCount, (desiredLength / 0.1).toInt())

        // Create the measuring line
        measuringLine = Node()
        measuringLine?.setParent(scene)

        // Create knots along the measuring line
        for (i in 0 until calculatedKnotCount) {
            val knot = Node()
            knot.setParent(measuringLine)
            knots.add(knot)
        }

        // Update the measuring line based on the user-specified length
        updateMeasuringLine(desiredLength)
    }
    private fun updateMeasuringLine(lengthInMeters: Float) {
        val knotSpacing = lengthInMeters / (KNOT_COUNT - 1)

        measuringLine?.localPosition = Vector3(0f, 0f, -lengthInMeters / 2f)

        for ((index, knot) in knots.withIndex()) {
            knot.localPosition = Vector3(0f, 0f, index * knotSpacing)
        }


        // Display the length in the middle of the measuring line
        displayLength(lengthInMeters)
    }

    private fun displayLength(lengthInMeters: Any) {
        Log.d("Measuring", "Length: $lengthInMeters meters")

    }

    private fun clearMeasurements() {
        measuringLine?.setParent(null)
        measuringLine = null
        knots.forEach { it.setParent(null) }
        knots.clear()
    }


    private fun showDefaultModel() {
        // Remove measuring content if any
        // Add or update nodes to display the default model
        val anchorNode = AnchorNode()
        anchorNode.setParent(scene)
        val modelNode = TransformableNode(arFragment.transformationSystem)
        modelNode.setParent(anchorNode)
        modelNode.renderable = model
        // Additional logic for positioning, scaling, etc.
        // ...
    }

    private suspend fun loadModels(modelUri : String?) {
        model = ModelRenderable.builder()
            .setSource(context, Uri.parse(modelUri))
            .setIsFilamentGltf(true)
            .await()
        modelView = ViewRenderable.builder()
            .setView(context, R.layout.viewpager_image_item)
            .await()
    }


private fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
    if (model == null || modelView == null) {
        Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
        return
    }

    // Create the Anchor.
    scene.addChild(AnchorNode(hitResult.createAnchor()).apply {
        // Create the transformable model and add it to the anchor.
        val transformableNode = TransformableNode(arFragment.transformationSystem).apply {
            renderable = model
            renderableInstance.setCulling(false)
            renderableInstance.animate(true).start()
        }

        // Add the View
        val viewNode = Node().apply {
            // Define the relative position
            localPosition = Vector3(0.0f, 1f, 0.0f)
            localScale = Vector3(0.7f, 0.7f, 2.0f)
            renderable = modelView
        }

        addChild(transformableNode)
        transformableNode.addChild(viewNode)

        // Set an OnTouchListener to the TransformableNode to enable user interaction
             transformableNode.setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record the initial touch position for reference
                        initialTouchX = motionEvent.rawX
                        initialTouchY = motionEvent.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Calculate the change in touch position
                        val deltaX = motionEvent.rawX - initialTouchX
                        val deltaY = motionEvent.rawY - initialTouchY

                        // Update the transformation based on user input
                        transformableNode.worldPosition.x += deltaX / 1000f
                        transformableNode.worldPosition.y -= deltaY / 1000f

                        // Record the current touch position for the next iteration
                        initialTouchX = motionEvent.rawX
                        initialTouchY = motionEvent.rawY
                    }
                    MotionEvent.ACTION_UP -> {
                        // Handle touch release if needed

                        // Stop any ongoing animations
                        transformableNode.renderableInstance.animate(false).start()

                        // Perform additional actions, if necessary
                        // ...
                    }
                }
                // Return true to consume the event
                true
            }

        })

     fun calculateDistance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

     fun startMeasuringPoints() {
        if (measuringInProgress) {
            // If measuring is in progress, hide measurements and show the product model
            clearMeasurements()
            showProductModel()
            measuringInProgress = false
        } else {
            // If measuring is not in progress, start measuring
            hideProductModel()
            // Assuming the user will tap twice to specify starting and ending points
            Toast.makeText(context, "Tap on the starting point.", Toast.LENGTH_SHORT).show()
            var startingPose: Pose? = null

            arSceneView.setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record the starting point for measurement
                        val hitResult = arSceneView.arFrame?.hitTest(motionEvent)?.firstOrNull()
                        startingPose = hitResult?.hitPose
                        if (startingPose != null) {
                            Toast.makeText(context, "Tap on the ending point.", Toast.LENGTH_SHORT).show()
                            arSceneView.setOnTouchListener { _, motionEvent ->
                                when (motionEvent.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        // Record the ending point for measurement
                                        val hitResult = arSceneView.arFrame?.hitTest(motionEvent)?.firstOrNull()
                                        val endingPose = hitResult?.hitPose
                                        if (endingPose != null && startingPose != null) {
                                            // Calculate the distance between starting and ending points
                                            val desiredLength = calculateDistance(startingPose!!, endingPose)
                                            // Start measuring with the calculated length
                                            createMeasuringLine(desiredLength)
                                            measuringInProgress = true
                                        }
                                        // Reset the touch listener
                                        arSceneView.setOnTouchListener(null)
                                    }
                                }
                                true
                            }
                        }
                    }
                }
                true
            }
        }
    }

}



}
