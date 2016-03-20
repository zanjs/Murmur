package cn.nekocode.murmur.presentation.main

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.ProgressDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.support.v7.graphics.Palette
import android.text.TextUtils
import android.util.Patterns
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.*
import butterknife.bindView
import cn.nekocode.kotgo.component.presentation.BaseFragment
import cn.nekocode.kotgo.component.util.showToast
import cn.nekocode.murmur.R
import cn.nekocode.murmur.data.dto.DoubanSong
import cn.nekocode.murmur.data.dto.Murmur
import cn.nekocode.murmur.util.CircleTransform
import cn.nekocode.murmur.util.ImageUtil
import cn.nekocode.murmur.view.ShaderRenderer
import com.pnikosis.materialishprogress.ProgressWheel
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.jetbrains.anko.*
import kotlin.properties.Delegates

class MainFragment: BaseFragment(), MainPresenter.ViewInterface, View.OnTouchListener {
    override val layoutId: Int = R.layout.fragment_main
    val presenter = MainPresenter(this)

    val surfaceView: GLSurfaceView by bindView(R.id.surfaceView)
    var renderer: ShaderRenderer by Delegates.notNull<ShaderRenderer>()

    val backgroundView: View by bindView(R.id.relativeLayout)
    val coverImageView: ImageSwitcher by bindView(R.id.coverImageView)
    val progressWheel: ProgressWheel by bindView(R.id.progressWheel)
    val titleTextView: TextView by bindView(R.id.titleTextView)
    val performerTextView: TextView by bindView(R.id.performerTextView)
    val murmursTextView: TextView by bindView(R.id.murmursTextView)
    val timeTextView: TextView by bindView(R.id.timeTextView)

    var loginProgressDialog by Delegates.notNull<ProgressDialog>()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGLSufaceview()
        setupCoverView()

        oldBackgroundColor = resources.getColor(R.color.color_primary)
        oldTextColor = Color.WHITE

        loginProgressDialog = ProgressDialog(activity).apply {
            setMessage("Loging...")
            setCancelable(false)
        }

        presenter.init()
    }

    fun setupGLSufaceview() {
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setOnTouchListener(this)

        val shader = resources.openRawResource(R.raw.shader).reader().readText()

        renderer = ShaderRenderer(activity, shader).apply {
            setBackColor(resources.getColor(R.color.color_primary_dark))
            setSpeed(0.6f)

            surfaceView.setRenderer(this)
        }
    }

    fun setupCoverView() {
        coverImageView.setFactory {
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        coverImageView.apply {
            inAnimation = AnimationUtils.loadAnimation(activity, android.R.anim.fade_in)
            inAnimation.duration = ANIMATION_DURATION

            outAnimation = AnimationUtils.loadAnimation(activity, android.R.anim.fade_out)
            outAnimation.duration = ANIMATION_DURATION

            setImageResource(R.drawable.transparent)
        }
    }

    override fun showLoginDialog() {
        AlertDialogBuilder(activity).apply {
            title("Login Your Douban Account")
            cancellable(false)

            var emailEdit: EditText? = null
            var pwdEdit: EditText? = null
            customView {
                verticalLayout() {
                    padding = dip(30)

                    emailEdit = editText {
                        hint = "Email"
                        textSize = 14f
                    }

                    pwdEdit = editText {
                        hint = "Password"
                        textSize = 14f
                    }
                }
            }
            positiveButton {
                val email = emailEdit?.text.toString()
                val pwd = pwdEdit?.text.toString()

                if(!isEmail(email)) {
                    toast("Email address is invaild.")

                } else if (TextUtils.isEmpty(pwd)) {
                    toast("Password is invaild.")

                } else {
                    presenter.login(email, pwd)
                    loginProgressDialog.show()
                }
            }
        }.show()
    }

    override fun loginSuccess() {
        loginProgressDialog.dismiss()
    }

    override fun loginFailed() {
        showLoginDialog()
        loginProgressDialog.dismiss()
    }

    fun isEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun toast(msg: String) {
        showToast(msg)
    }

    override fun murmursChange(murmurs: List<Murmur>) {
        var text = ""
        val last = murmurs.lastOrNull()
        murmurs.forEach {
            text += it.name
            if(it != last) text += ", "
        }
        murmursTextView.text = text
    }

    val target = object: Target {
        override fun onPrepareLoad(drawable: Drawable?) {
        }

        override fun onBitmapFailed(drawable: Drawable?) {
            coverImageView.setImageResource(R.drawable.transparent)
        }

        override fun onBitmapLoaded(bitmap: Bitmap?, p1: Picasso.LoadedFrom?) {
            bitmap ?: return

            switchPalette(bitmap)
            coverImageView.setImageDrawable(ImageUtil.bitmap2Drawable(bitmap))
        }
    }

    override fun songChange(song: DoubanSong) {
        isPaletteChanging = true

        song.apply {
            titleTextView.text = title
            performerTextView.text = artist
            timeTextView.text = length.toString()

            Picasso.with(activity).apply {
                cancelRequest(target)
                load(picture).transform(CircleTransform()).into(target)
            }
        }

        renderer.setSpeed(1.0f)
    }

    val ANIMATION_DURATION = 800L
    var oldBackgroundColor = 0
    var oldTextColor = 0
    var backgroundColorAnimator: ValueAnimator? = null
    var textColorAnimator: ValueAnimator? = null

    fun switchPalette(bitmap: Bitmap) {
        Palette.from(bitmap).generate {
            val swatch = it.darkVibrantSwatch ?: it.vibrantSwatch ?: it.darkMutedSwatch ?: it.lightMutedSwatch
            swatch!!

            fun createColorAnimator(sourceColor: Int,
                                    targetColor: Int,
                                    updateListener: (it: ValueAnimator)->Unit): ValueAnimator
                    = ValueAnimator.ofObject(ArgbEvaluator(), sourceColor, targetColor).apply {
                duration = ANIMATION_DURATION + 100
                interpolator = LinearInterpolator()
                addUpdateListener(updateListener)
            }

            backgroundColorAnimator?.cancel()
            backgroundColorAnimator = createColorAnimator(oldBackgroundColor, swatch.rgb) {
                val color = it.animatedValue as Int

                backgroundView.backgroundColor = color
                renderer.setBackColor(color)
                if (Build.VERSION.SDK_INT >= 21) {
                    activity.window.statusBarColor = color
                }

                oldBackgroundColor = color
            }
            backgroundColorAnimator?.start()

            textColorAnimator?.cancel()
            textColorAnimator = createColorAnimator(oldTextColor, swatch.titleTextColor) {
                val color = it.animatedValue as Int

                titleTextView.textColor = color
                performerTextView.textColor = color
                murmursTextView.textColor = color
                timeTextView.textColor = color
                progressWheel.barColor = color

                oldTextColor = color
            }
            textColorAnimator?.start()

            listenAnimation(textColorAnimator!!)
        }
    }

    var isPaletteChanging = false
    fun listenAnimation(animator: Animator) {
        animator.addListener(object: Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                isPaletteChanging = false
                progressWheel.visibility = View.INVISIBLE
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }

        })
    }

    override fun onTouch(view: View?, event: MotionEvent?) = gestureDetector.onTouchEvent(event)

    val gestureDetector by lazy {
        GestureDetector(activity, object: GestureDetector.OnGestureListener {
            val FLING_MIN_DISTANCE = dip(100)
            val FLING_MIN_DISTANCE_Y = dip(50)
            val FLING_MIN_VELOCITY = 1

            var lastestTapTime = 0L
            override fun onSingleTapUp(p0: MotionEvent?): Boolean {
                val nowTapTime = System.currentTimeMillis()
                if (nowTapTime - lastestTapTime < 800) {
                    Toast.makeText(activity, "Double tap", Toast.LENGTH_SHORT).show()

                    lastestTapTime = 0
                    return false
                }

                lastestTapTime = nowTapTime
                return true
            }

            override fun onDown(p0: MotionEvent?): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (Math.abs(e1.y - e2.y) > FLING_MIN_DISTANCE_Y || isPaletteChanging)
                    return false

                if (e1.x - e2.x > FLING_MIN_DISTANCE
                        && Math.abs(velocityX) > FLING_MIN_VELOCITY) {
                    //向右滑动
                    progressWheel.visibility = View.VISIBLE
                    presenter.nextSong()

                } else if (e2.x - e1.x > FLING_MIN_DISTANCE
                        && Math.abs(velocityX) > FLING_MIN_VELOCITY) {
                    //向左滑动
                    progressWheel.visibility = View.VISIBLE
                    presenter.nextSong()
                }

                return false
            }

            override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
                return true
            }

            override fun onShowPress(p0: MotionEvent?) {
            }

            override fun onLongPress(p0: MotionEvent?) {
            }

        })
    }
}
