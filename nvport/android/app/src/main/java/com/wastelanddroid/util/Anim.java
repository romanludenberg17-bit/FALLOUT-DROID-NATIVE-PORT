package com.wastelanddroid.util;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

public final class Anim {
    public static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

    private Anim() {
    }

    public static void enter(View view, long j) {
        view.setAlpha(0.0f);
        view.setTranslationY(view.getResources().getDisplayMetrics().density * 28.0f);
        view.animate().alpha(1.0f).translationY(0.0f).setStartDelay(j).setDuration(520L).setInterpolator(EMPHASIZED).withLayer().start();
    }

    public static void enterFromTop(View view, long j) {
        view.setAlpha(0.0f);
        view.setTranslationY((-view.getResources().getDisplayMetrics().density) * 24.0f);
        view.animate().alpha(1.0f).translationY(0.0f).setStartDelay(j).setDuration(600L).setInterpolator(EMPHASIZED).start();
    }

    public static void stagger(ViewGroup viewGroup, long j, long j2) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            enter(viewGroup.getChildAt(i), (i * j2) + j);
        }
    }

    public static void pressable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() { // from class: com.wastelanddroid.util.Anim$$ExternalSyntheticLambda0
            @Override // android.view.View.OnTouchListener
            public final boolean onTouch(View view2, MotionEvent motionEvent) {
                return Anim.lambda$pressable$0(view2, motionEvent);
            }
        });
    }

    static /* synthetic */ boolean lambda$pressable$0(View view, MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).setInterpolator(new DecelerateInterpolator()).setStartDelay(0L).start();
            return false;
        }
        if (actionMasked != 1 && actionMasked != 3) {
            return false;
        }
        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(260L).setInterpolator(new OvershootInterpolator(3.0f)).setStartDelay(0L).start();
        return false;
    }

    public static Animator pulse(View view) {
        ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.SCALE_X, 1.0f, 1.035f);
        ObjectAnimator objectAnimatorOfFloat2 = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.SCALE_Y, 1.0f, 1.035f);
        ObjectAnimator[] objectAnimatorArr = {objectAnimatorOfFloat, objectAnimatorOfFloat2};
        for (int i = 0; i < 2; i++) {
            ObjectAnimator objectAnimator = objectAnimatorArr[i];
            objectAnimator.setRepeatMode(2);
            objectAnimator.setRepeatCount(-1);
            objectAnimator.setDuration(1100L);
            objectAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(objectAnimatorOfFloat, objectAnimatorOfFloat2);
        animatorSet.start();
        return animatorSet;
    }

    public static Animator flicker(View view) {
        ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.ALPHA, 1.0f, 0.82f, 1.0f, 0.9f, 1.0f);
        objectAnimatorOfFloat.setDuration(2600L);
        objectAnimatorOfFloat.setRepeatCount(-1);
        objectAnimatorOfFloat.start();
        return objectAnimatorOfFloat;
    }
}
