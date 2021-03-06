package com.mozz.htmlnative.script.lua;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.mozz.htmlnative.HNRenderer;
import com.mozz.htmlnative.HNSandBoxContext;
import com.mozz.htmlnative.InheritStyleStack;
import com.mozz.htmlnative.css.Styles;
import com.mozz.htmlnative.css.stylehandler.LayoutStyleHandler;
import com.mozz.htmlnative.css.stylehandler.StyleHandler;
import com.mozz.htmlnative.css.stylehandler.StyleHandlerFactory;
import com.mozz.htmlnative.dom.AttachedElement;
import com.mozz.htmlnative.dom.DomElement;
import com.mozz.htmlnative.exception.AttrApplyException;
import com.mozz.htmlnative.parser.CssParser;
import com.mozz.htmlnative.utils.MainHandlerUtils;
import com.mozz.htmlnative.view.LayoutParamsLazyCreator;

import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Yang Tao, 17/3/23.
 */

class LView extends LuaTable {

    private View mView;
    volatile boolean mAdded;
    private volatile boolean mCreated;
    private HNSandBoxContext mContext;
    private DomElement mDomElement;
    private Map<String, Object> mInlineStyleRaw;

    private static StringBuilder sParserBuffer = new StringBuilder();
    private final Object mLock = new Object();

    LView(final DomElement domElement, Map<String, Object> inlineStyle, final HNSandBoxContext
            context) {
        mDomElement = domElement;
        mInlineStyleRaw = inlineStyle;
        mContext = context;
        mCreated = false;
        mAdded = false;

        initLuaFunction();
    }

    /**
     * Used only by {@link LFindViewById}, which will look up view in existing view tree.
     */
    LView(final View v, final HNSandBoxContext context) {
        this((DomElement) v.getTag(), null, context);
        mView = v;
        mCreated = true;
        mAdded = true;
    }


    private void initLuaFunction() {
        set("toString", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (mCreated) {
                    return LView.valueOf(mView.toString());
                } else {
                    return LuaValue.NIL;
                }
            }
        });

        set("setAttribute", new OneArgFunction() {
            @Override
            public LuaValue call(final LuaValue arg) {
                if (mCreated) {
                    String style = arg.tojstring();
                    final Map<String, Object> styleMaps = new HashMap<>();
                    CssParser.parseInlineStyle(style, sParserBuffer, styleMaps);

                    MainHandlerUtils.instance().post(new Runnable() {
                        @Override
                        public void run() {
                            LayoutParamsLazyCreator tempCreator = new LayoutParamsLazyCreator();
                            ViewGroup parent = (mView.getParent() != null && mView.getParent()
                                    instanceof ViewGroup) ? (ViewGroup) mView.getParent() : null;
                            try {
                                HNRenderer.renderStyle(mView.getContext(), mContext, mView,
                                        mDomElement, tempCreator, parent, styleMaps, false, null);
                                LayoutParamsLazyCreator.createLayoutParams(tempCreator, mView
                                        .getLayoutParams());
                                mView.requestLayout();

                            } catch (AttrApplyException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {
                    final Map<String, Object> newStyle = new HashMap<>();
                    CssParser.parseInlineStyle(arg.tojstring(), sParserBuffer, newStyle);

                    MainHandlerUtils.instance().post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mLock) {
                                if (mInlineStyleRaw != null) {
                                    mInlineStyleRaw.putAll(newStyle);
                                }
                            }
                        }
                    });
                }
                return NIL;
            }
        });

        set("id", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (mCreated) {
                            Object obj = mView.getTag();
                            if (obj != null && obj instanceof DomElement) {
                                return LuaString.valueOf(((DomElement) obj).getId());
                            }
                        }
                        return LuaString.valueOf("");
                    }
                }

        );

        set("className", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        String[] classes = ((AttachedElement) mView.getTag()).getClazz();
                        LuaTable classesLua = new LuaTable();
                        if (classes != null && classes.length > 0) {
                            for (String clazz : classes) {
                                if (clazz != null) {
                                    classesLua.add(LuaValue.valueOf(clazz));
                                }
                            }

                            return classesLua;
                        }
                        return LuaTable.NIL;
                    }
                }

        );

        set("appendChild", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        if (mCreated && arg instanceof LView && mView instanceof ViewGroup) {
                            final LView child = (LView) arg;
                            if (!child.mCreated) {
                                if (!child.mAdded) {
                                    MainHandlerUtils.instance().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            LayoutParamsLazyCreator creator = new
                                                    LayoutParamsLazyCreator();
                                            try {
                                                // This must be invoked before HNRenderer.createView
                                                child.mDomElement.setParent(mDomElement);

                                                // Compute the inherit style of parent
                                                InheritStyleStack inheritStyleStack = HNRenderer
                                                        .computeInheritStyle(mView);

                                                child.mView = HNRenderer.createView(null, child
                                                        .mDomElement, child.mContext, (ViewGroup)
                                                        mView, mView.getContext(), null, creator,
                                                        child.mContext.getSegment().getStyleSheet
                                                                (), inheritStyleStack);

                                                try {
                                                    Map<String, Object> inlineStyles;
                                                    synchronized (mLock) {
                                                        inlineStyles = child.mInlineStyleRaw;

                                                    }
                                                    HNRenderer.renderStyle(child.mView.getContext
                                                            (), mContext, child.mView, child
                                                            .mDomElement, creator, (ViewGroup)
                                                            mView, inlineStyles, false,
                                                            inheritStyleStack);
                                                } catch (AttrApplyException e) {
                                                    e.printStackTrace();
                                                }

                                                child.mCreated = true;
                                                ((ViewGroup) mView).addView(child.mView,
                                                        LayoutParamsLazyCreator
                                                                .createLayoutParams(mView,
                                                                        creator));


                                                child.mAdded = true;

                                                // consume the inline style
                                                synchronized (mLock) {
                                                    child.mInlineStyleRaw = null;
                                                }

                                            } catch (HNRenderer.HNRenderException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            }
                        }
                        return LuaValue.NIL;
                    }
                }

        );

        set("insertBefore", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        if (mCreated && arg instanceof LView && mView instanceof ViewGroup) {
                            final LView child = (LView) arg;
                            if (!child.mCreated) {
                                if (!child.mAdded) {
                                    MainHandlerUtils.instance().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            LayoutParamsLazyCreator creator = new
                                                    LayoutParamsLazyCreator();
                                            try {
                                                // This must be invoked before HNRenderer.createView
                                                child.mDomElement.setParent(mDomElement);

                                                // Compute the inherit style of parent
                                                InheritStyleStack inheritStyleStack = HNRenderer
                                                        .computeInheritStyle(mView);

                                                child.mView = HNRenderer.createView(null, child
                                                        .mDomElement, child.mContext, (ViewGroup)
                                                        mView, mView.getContext(), null, creator,
                                                        child.mContext.getSegment().getStyleSheet
                                                                (), inheritStyleStack);

                                                Map<String, Object> inlineStyles;
                                                synchronized (mLock) {
                                                    inlineStyles = child.mInlineStyleRaw;

                                                }

                                                try {
                                                    HNRenderer.renderStyle(child.mView.getContext
                                                            (), mContext, child.mView, child
                                                            .mDomElement, creator, (ViewGroup)
                                                            mView, inlineStyles, false,
                                                            inheritStyleStack);
                                                } catch (AttrApplyException e) {
                                                    e.printStackTrace();
                                                }

                                                child.mCreated = true;

                                                ((ViewGroup) mView).addView(child.mView, 0,
                                                        LayoutParamsLazyCreator
                                                                .createLayoutParams(mView,
                                                                        creator));


                                                child.mAdded = true;

                                                synchronized (mLock) {
                                                    // consume the inline style
                                                    child.mInlineStyleRaw = null;
                                                }
                                            } catch (HNRenderer.HNRenderException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            }
                        }
                        return LuaValue.NIL;
                    }
                }

        );

        set("removeChild", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        if (mAdded && arg instanceof LView && mView instanceof ViewGroup) {
                            final LView toRemoved = (LView) arg;

                            MainHandlerUtils.instance().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (toRemoved.mAdded) {
                                        ((ViewGroup) mView).removeView(toRemoved.mView);
                                        toRemoved.mAdded = false;
                                    }
                                }
                            });

                        }
                        return LuaValue.NIL;
                    }
                }

        );

        set("childNodes", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (mView instanceof ViewGroup && mAdded) {
                            LuaTable children = new LuaTable();

                            int childCount = ((ViewGroup) mView).getChildCount();
                            for (int i = 0; i < childCount; i++) {
                                LView lView = new LView(((ViewGroup) mView).getChildAt(i),
                                        mContext);
                                children.add(lView);
                            }
                            return children;
                        }
                        return LuaValue.NIL;
                    }
                }

        );

        set("getAttribute", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        if (mAdded && mCreated) {
                            StyleHandler styleHandler = StyleHandlerFactory.get(mView);
                            StyleHandler extraHandler = StyleHandlerFactory.extraGet(mView);
                            LayoutStyleHandler parentHandler = StyleHandlerFactory.parentGet(mView);
                            Object object = Styles.getStyle(mView, arg.tojstring(), styleHandler,
                                    extraHandler, parentHandler);

                            if (object != null) {
                                return LuaString.valueOf(object.toString());
                            } else {
                                return LuaValue.NIL;
                            }
                        } else {
                            return LuaValue.NIL;
                        }
                    }
                }

        );

        set("tagName", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (mCreated) {
                            AttachedElement attachedElement = (AttachedElement) mView.getTag();
                            return LuaString.valueOf(attachedElement.getType());
                        } else {
                            return LuaString.valueOf("");
                        }
                    }
                }

        );

        set("parentNode", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (mCreated && mAdded) {
                            ViewParent parent = mView.getParent();
                            if (parent instanceof ViewGroup) {
                                return new LView((View) parent, mContext);
                            }
                        }
                        return LuaValue.NIL;
                    }
                }

        );


        set("hasChildNode", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        if (mCreated && mAdded) {
                            if (mView instanceof ViewGroup) {
                                boolean hasChild = ((ViewGroup) mView).getChildCount() > 0;
                                return LuaBoolean.valueOf(hasChild);
                            }
                        }

                        return LuaBoolean.FALSE;
                    }
                }

        );
    }


    @Override
    public int type() {
        return TUSERDATA;
    }

    @Override
    public String typename() {
        return TYPE_NAMES[3];
    }

}
