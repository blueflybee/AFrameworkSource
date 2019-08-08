/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.res;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.ActivityInfo.Config;
import android.content.res.Resources.Theme;
import android.graphics.Color;

import com.android.internal.R;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.GrowingArrayUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.SparseArray;
import android.util.StateSet;
import android.util.Xml;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 *
 * Lets you map {@link android.view.View} state sets to colors.
 * <p>
 * {@link android.content.res.ColorStateList}s are created from XML resource files defined in the
 * "color" subdirectory directory of an application's resource directory. The XML file contains
 * a single "selector" element with a number of "item" elements inside. For example:
 * <pre>
 * &lt;selector xmlns:android="http://schemas.android.com/apk/res/android"&gt;
 *   &lt;item android:state_focused="true"
 *           android:color="@color/sample_focused" /&gt;
 *   &lt;item android:state_pressed="true"
 *           android:state_enabled="false"
 *           android:color="@color/sample_disabled_pressed" /&gt;
 *   &lt;item android:state_enabled="false"
 *           android:color="@color/sample_disabled_not_pressed" /&gt;
 *   &lt;item android:color="@color/sample_default" /&gt;
 * &lt;/selector&gt;
 * </pre>
 *
 * This defines a set of state spec / color pairs where each state spec specifies a set of
 * states that a view must either be in or not be in and the color specifies the color associated
 * with that spec.
 *
 * <a name="StateSpec"></a>
 * <h3>State specs</h3>
 * <p>
 * Each item defines a set of state spec and color pairs, where the state spec is a series of
 * attributes set to either {@code true} or {@code false} to represent inclusion or exclusion. If
 * an attribute is not specified for an item, it may be any value.
 * <p>
 * For example, the following item will be matched whenever the focused state is set; any other
 * states may be set or unset:
 * <pre>
 * &lt;item android:state_focused="true"
 *         android:color="@color/sample_focused" /&gt;
 * </pre>
 * <p>
 * Typically, a color state list will reference framework-defined state attributes such as
 * {@link android.R.attr#state_focused android:state_focused} or
 * {@link android.R.attr#state_enabled android:state_enabled}; however, app-defined attributes may
 * also be used.
 * <p>
 * <strong>Note:</strong> The list of state specs will be matched against in the order that they
 * appear in the XML file. For this reason, more-specific items should be placed earlier in the
 * file. An item with no state spec is considered to match any set of states and is generally
 * useful as a final item to be used as a default.
 * <p>
 * If an item with no state spec is placed before other items, those items
 * will be ignored.
 *
 * <a name="ItemAttributes"></a>
 * <h3>Item attributes</h3>
 * <p>
 * Each item must define an {@link android.R.attr#color android:color} attribute, which may be
 * an HTML-style hex color, a reference to a color resource, or -- in API 23 and above -- a theme
 * attribute that resolves to a color.
 * <p>
 * Starting with API 23, items may optionally define an {@link android.R.attr#alpha android:alpha}
 * attribute to modify the base color's opacity. This attribute takes a either floating-point value
 * between 0 and 1 or a theme attribute that resolves as such. The item's overall color is
 * calculated by multiplying by the base color's alpha channel by the {@code alpha} value. For
 * example, the following item represents the theme's accent color at 50% opacity:
 * <pre>
 * &lt;item android:state_enabled="false"
 *         android:color="?android:attr/colorAccent"
 *         android:alpha="0.5" /&gt;
 * </pre>
 *
 * <a name="DeveloperGuide"></a>
 * <h3>Developer guide</h3>
 * <p>
 * For more information, see the guide to
 * <a href="{@docRoot}guide/topics/resources/color-list-resource.html">Color State
 * List Resource</a>.
 *
 * @attr ref android.R.styleable#ColorStateListItem_alpha
 * @attr ref android.R.styleable#ColorStateListItem_color
 */
public class ColorStateList extends ComplexColor implements Parcelable {
    private static final String TAG = "ColorStateList";

    private static final int DEFAULT_COLOR = Color.RED;
    private static final int[][] EMPTY = new int[][] { new int[0] };

    /** Thread-safe cache of single-color ColorStateLists. */
    private static final SparseArray<WeakReference<ColorStateList>> sCache = new SparseArray<>();

    /** Lazily-created factory for this color state list. */
    private ColorStateListFactory mFactory;

    private int[][] mThemeAttrs;
    private @Config int mChangingConfigurations;

    private int[][] mStateSpecs;
    private int[] mColors;
    private int mDefaultColor;
    private boolean mIsOpaque;

    private ColorStateList() {
        // Not publicly instantiable.
    }

    /**
     * ok>>
     * Creates a ColorStateList that returns the specified mapping from
     * states to colors.
     */
    //可以使用代码创建ColorStateList对象，设置states和colors，然后为view设置ColorStateList
    public ColorStateList(int[][] states, @ColorInt int[] colors) {
        mStateSpecs = states;
        mColors = colors;

        onColorsChanged();
    }

    /**
     * ok>>
     * @return A ColorStateList containing a single color.
     */
    //根据color为key，在sCache缓存中搜索对应的ColorStateList
    @NonNull
    public static ColorStateList valueOf(@ColorInt int color) {
        synchronized (sCache) {
            //在缓存列表中搜索sCache，找到则返回
            final int index = sCache.indexOfKey(color);
            if (index >= 0) {
                final ColorStateList cached = sCache.valueAt(index).get();
                if (cached != null) {
                    return cached;
                }

                // Prune missing entry.
                //color值对应的ColorStateList为空，说明这个key是无效的，需要删除无效的映射
                sCache.removeAt(index);
            }

            // Prune the cache before adding new items.
            //在添加新的ColorStateList前，清空所有的无效映射
            final int N = sCache.size();
            for (int i = N - 1; i >= 0; i--) {
                if (sCache.valueAt(i).get() == null) {
                    sCache.removeAt(i);
                }
            }

            //没有映射则创建一个放入缓存
            final ColorStateList csl = new ColorStateList(EMPTY, new int[] { color });
            sCache.put(color, new WeakReference<>(csl));
            return csl;
        }
    }

    /**
     * ok>>
     * Creates a ColorStateList with the same properties as another
     * ColorStateList.
     * <p>
     * The properties of the new ColorStateList can be modified without
     * affecting the source ColorStateList.
     *
     * @param orig the source color state list
     */
    private ColorStateList(ColorStateList orig) {
        if (orig != null) {
            mChangingConfigurations = orig.mChangingConfigurations;
            mStateSpecs = orig.mStateSpecs;
            mDefaultColor = orig.mDefaultColor;
            mIsOpaque = orig.mIsOpaque;

            // Deep copy, these may change due to applyTheme().
            mThemeAttrs = orig.mThemeAttrs.clone();
            mColors = orig.mColors.clone();
        }
    }

    /**
     * ok>>
     * Creates a ColorStateList from an XML document.
     *
     * @param r Resources against which the ColorStateList should be inflated.
     * @param parser Parser for the XML document defining the ColorStateList.
     * @return A new color state list.
     *
     * @deprecated Use #createFromXml(Resources, XmlPullParser parser, Theme)
     */
    @NonNull
    @Deprecated
    public static ColorStateList createFromXml(Resources r, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        return createFromXml(r, parser, null);
    }

    /**
     * ok>>
     * Creates a ColorStateList from an XML document using given a set of
     * {@link Resources} and a {@link Theme}.
     *
     * @param r Resources against which the ColorStateList should be inflated.
     * @param parser Parser for the XML document defining the ColorStateList.
     * @param theme Optional theme to apply to the color state list, may be
     *              {@code null}.
     * @return A new color state list.
     */
    @NonNull
    public static ColorStateList createFromXml(@NonNull Resources r, @NonNull XmlPullParser parser,
            @Nullable Theme theme) throws XmlPullParserException, IOException {
        final AttributeSet attrs = Xml.asAttributeSet(parser);

        int type;
        //搜索起始位置的tag
        while ((type = parser.next()) != XmlPullParser.START_TAG
                   && type != XmlPullParser.END_DOCUMENT) {
            // Seek parser to start tag.
        }

        //没有搜索到抛出异常
        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        return createFromXmlInner(r, parser, attrs, theme);
    }

    /**
     * ok>>
     * Create from inside an XML document. Called on a parser positioned at a
     * tag in an XML document, tries to create a ColorStateList from that tag.
     *
     * @throws XmlPullParserException if the current tag is not &lt;selector>
     * @return A new color state list for the current tag.
     */
    @NonNull
    static ColorStateList createFromXmlInner(@NonNull Resources r,
            @NonNull XmlPullParser parser, @NonNull AttributeSet attrs, @Nullable Theme theme)
            throws XmlPullParserException, IOException {
        //找到第一个标签了，获取标签名称
        final String name = parser.getName();
        //ColorState list xml文件要求root tag名称为selector，否则视为无效标签
        if (!name.equals("selector")) {
            throw new XmlPullParserException(
                    parser.getPositionDescription() + ": invalid color state list tag " + name);
        }

        final ColorStateList colorStateList = new ColorStateList();
        //开始inflate，把xml文档中的item信息，初始化一个colorStateList对象
        colorStateList.inflate(r, parser, attrs, theme);
        return colorStateList;
    }

    /**
     * ok>>
     * Creates a new ColorStateList that has the same states and colors as this
     * one but where each color has the specified alpha value (0-255).
     *
     * @param alpha The new alpha channel value (0-255).
     * @return A new color state list.
     */
    @NonNull
    public ColorStateList withAlpha(int alpha) {
        final int[] colors = new int[mColors.length];
        final int len = colors.length;
        //给每个color值添加同样的alpha值
        for (int i = 0; i < len; i++) {
            colors[i] = (mColors[i] & 0xFFFFFF) | (alpha << 24);
        }

        //用新的colors创建ColorStateList对象
        return new ColorStateList(mStateSpecs, colors);
    }

    /**
     * ok>>
     * Fill in this object based on the contents of an XML "selector" element.
     */
    private void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
            @NonNull AttributeSet attrs, @Nullable Theme theme)
            throws XmlPullParserException, IOException {
        final int innerDepth = parser.getDepth()+1;
        int depth;
        int type;

        @Config int changingConfigurations = 0;
        int defaultColor = DEFAULT_COLOR;

        boolean hasUnresolvedAttrs = false;

        int[][] stateSpecList = ArrayUtils.newUnpaddedArray(int[].class, 20);
        int[][] themeAttrsList = new int[stateSpecList.length][];
        int[] colorList = new int[stateSpecList.length];
        int listSize = 0;

        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
               && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            //搜索第一个item标签
            if (type != XmlPullParser.START_TAG || depth > innerDepth
                    || !parser.getName().equals("item")) {
                continue;
            }

            final TypedArray a = Resources.obtainAttributes(r, theme, attrs,
                    R.styleable.ColorStateListItem);
            final int[] themeAttrs = a.extractThemeAttrs();
            final int baseColor = a.getColor(R.styleable.ColorStateListItem_color, Color.MAGENTA);
            final float alphaMod = a.getFloat(R.styleable.ColorStateListItem_alpha, 1.0f);

            changingConfigurations |= a.getChangingConfigurations();

            a.recycle();

            // Parse all unrecognized attributes as state specifiers.
            int j = 0;
            final int numAttrs = attrs.getAttributeCount();
            int[] stateSpec = new int[numAttrs];
            for (int i = 0; i < numAttrs; i++) {
                final int stateResId = attrs.getAttributeNameResource(i);
                switch (stateResId) {
                    case R.attr.color:
                    case R.attr.alpha:
                        // Recognized attribute, ignore.
                        break;
                    default:
                        stateSpec[j++] = attrs.getAttributeBooleanValue(i, false)
                                ? stateResId : -stateResId;
                }
            }
            stateSpec = StateSet.trimStateSet(stateSpec, j);

            // Apply alpha modulation. If we couldn't resolve the color or
            // alpha yet, the default values leave us enough information to
            // modulate again during applyTheme().
            //添加透明度
            final int color = modulateColorAlpha(baseColor, alphaMod);
            if (listSize == 0 || stateSpec.length == 0) {
                defaultColor = color;
            }

            if (themeAttrs != null) {
                hasUnresolvedAttrs = true;
            }

            colorList = GrowingArrayUtils.append(colorList, listSize, color);
            themeAttrsList = GrowingArrayUtils.append(themeAttrsList, listSize, themeAttrs);
            stateSpecList = GrowingArrayUtils.append(stateSpecList, listSize, stateSpec);
            listSize++;
        }

        mChangingConfigurations = changingConfigurations;
        mDefaultColor = defaultColor;

        if (hasUnresolvedAttrs) {
            mThemeAttrs = new int[listSize][];
            System.arraycopy(themeAttrsList, 0, mThemeAttrs, 0, listSize);
        } else {
            mThemeAttrs = null;
        }

        mColors = new int[listSize];
        mStateSpecs = new int[listSize][];
        System.arraycopy(colorList, 0, mColors, 0, listSize);
        System.arraycopy(stateSpecList, 0, mStateSpecs, 0, listSize);

        onColorsChanged();
    }

    /**
     * ok>>
     * Returns whether a theme can be applied to this color state list, which
     * usually indicates that the color state list has unresolved theme
     * attributes.
     *
     * @return whether a theme can be applied to this color state list
     * @hide only for resource preloading
     */
    @Override
    public boolean canApplyTheme() {
        return mThemeAttrs != null;
    }

    /**
     * Applies a theme to this color state list.
     * <p>
     * <strong>Note:</strong> Applying a theme may affect the changing
     * configuration parameters of this color state list. After calling this
     * method, any dependent configurations must be updated by obtaining the
     * new configuration mask from {@link #getChangingConfigurations()}.
     *
     * @param t the theme to apply
     */
    private void applyTheme(Theme t) {
        if (mThemeAttrs == null) {
            return;
        }

        boolean hasUnresolvedAttrs = false;

        final int[][] themeAttrsList = mThemeAttrs;
        final int N = themeAttrsList.length;
        for (int i = 0; i < N; i++) {
            if (themeAttrsList[i] != null) {
                final TypedArray a = t.resolveAttributes(themeAttrsList[i],
                        R.styleable.ColorStateListItem);

                final float defaultAlphaMod;
                if (themeAttrsList[i][R.styleable.ColorStateListItem_color] != 0) {
                    // If the base color hasn't been resolved yet, the current
                    // color's alpha channel is either full-opacity (if we
                    // haven't resolved the alpha modulation yet) or
                    // pre-modulated. Either is okay as a default value.
                    defaultAlphaMod = Color.alpha(mColors[i]) / 255.0f;
                } else {
                    // Otherwise, the only correct default value is 1. Even if
                    // nothing is resolved during this call, we can apply this
                    // multiple times without losing of information.
                    defaultAlphaMod = 1.0f;
                }

                // Extract the theme attributes, if any, before attempting to
                // read from the typed array. This prevents a crash if we have
                // unresolved attrs.
                themeAttrsList[i] = a.extractThemeAttrs(themeAttrsList[i]);
                if (themeAttrsList[i] != null) {
                    hasUnresolvedAttrs = true;
                }

                final int baseColor = a.getColor(
                        R.styleable.ColorStateListItem_color, mColors[i]);
                final float alphaMod = a.getFloat(
                        R.styleable.ColorStateListItem_alpha, defaultAlphaMod);
                mColors[i] = modulateColorAlpha(baseColor, alphaMod);

                // Account for any configuration changes.
                mChangingConfigurations |= a.getChangingConfigurations();

                a.recycle();
            }
        }

        if (!hasUnresolvedAttrs) {
            mThemeAttrs = null;
        }

        onColorsChanged();
    }

    /**
     * Returns an appropriately themed color state list.
     *
     * @param t the theme to apply
     * @return a copy of the color state list with the theme applied, or the
     *         color state list itself if there were no unresolved theme
     *         attributes
     * @hide only for resource preloading
     */
    @Override
    public ColorStateList obtainForTheme(Theme t) {
        if (t == null || !canApplyTheme()) {
            return this;
        }

        final ColorStateList clone = new ColorStateList(this);
        clone.applyTheme(t);
        return clone;
    }

    /**
     * Returns a mask of the configuration parameters for which this color
     * state list may change, requiring that it be re-created.
     *
     * @return a mask of the changing configuration parameters, as defined by
     *         {@link android.content.pm.ActivityInfo}
     *
     * @see android.content.pm.ActivityInfo
     */
    public @Config int getChangingConfigurations() {
        return super.getChangingConfigurations() | mChangingConfigurations;
    }

    //ok>>
    private int modulateColorAlpha(int baseColor, float alphaMod) {
        if (alphaMod == 1.0f) {
            return baseColor;
        }

        final int baseAlpha = Color.alpha(baseColor);
        final int alpha = MathUtils.constrain((int) (baseAlpha * alphaMod + 0.5f), 0, 255);
        return (baseColor & 0xFFFFFF) | (alpha << 24);
    }

    /**
     * Indicates whether this color state list contains at least one state spec
     * and the first spec is not empty (e.g. match-all).
     *
     * @return True if this color state list changes color based on state, false
     *         otherwise.
     * @see #getColorForState(int[], int)
     */
    @Override
    public boolean isStateful() {
        return mStateSpecs.length >= 1 && mStateSpecs[0].length > 0;
    }

    /**
     * Return whether the state spec list has at least one item explicitly specifying
     * {@link android.R.attr#state_focused}.
     * @hide
     */
    public boolean hasFocusStateSpecified() {
        return StateSet.containsAttribute(mStateSpecs, R.attr.state_focused);
    }

    /**
     * Indicates whether this color state list is opaque, which means that every
     * color returned from {@link #getColorForState(int[], int)} has an alpha
     * value of 255.
     *
     * @return True if this color state list is opaque.
     */
    public boolean isOpaque() {
        return mIsOpaque;
    }

    /**
     * Return the color associated with the given set of
     * {@link android.view.View} states.
     *
     * @param stateSet an array of {@link android.view.View} states
     * @param defaultColor the color to return if there's no matching state
     *                     spec in this {@link ColorStateList} that matches the
     *                     stateSet.
     *
     * @return the color associated with that set of states in this {@link ColorStateList}.
     */
    public int getColorForState(@Nullable int[] stateSet, int defaultColor) {
        final int setLength = mStateSpecs.length;
        for (int i = 0; i < setLength; i++) {
            final int[] stateSpec = mStateSpecs[i];
            if (StateSet.stateSetMatches(stateSpec, stateSet)) {
                return mColors[i];
            }
        }
        return defaultColor;
    }

    /**
     * Return the default color in this {@link ColorStateList}.
     *
     * @return the default color in this {@link ColorStateList}.
     */
    @ColorInt
    public int getDefaultColor() {
        return mDefaultColor;
    }

    /**
     * Return the states in this {@link ColorStateList}. The returned array
     * should not be modified.
     *
     * @return the states in this {@link ColorStateList}
     * @hide
     */
    public int[][] getStates() {
        return mStateSpecs;
    }

    /**
     * Return the colors in this {@link ColorStateList}. The returned array
     * should not be modified.
     *
     * @return the colors in this {@link ColorStateList}
     * @hide
     */
    public int[] getColors() {
        return mColors;
    }

    /**
     * Returns whether the specified state is referenced in any of the state
     * specs contained within this ColorStateList.
     * <p>
     * Any reference, either positive or negative {ex. ~R.attr.state_enabled},
     * will cause this method to return {@code true}. Wildcards are not counted
     * as references.
     *
     * @param state the state to search for
     * @return {@code true} if the state if referenced, {@code false} otherwise
     * @hide Use only as directed. For internal use only.
     */
    public boolean hasState(int state) {
        final int[][] stateSpecs = mStateSpecs;
        final int specCount = stateSpecs.length;
        for (int specIndex = 0; specIndex < specCount; specIndex++) {
            final int[] states = stateSpecs[specIndex];
            final int stateCount = states.length;
            for (int stateIndex = 0; stateIndex < stateCount; stateIndex++) {
                if (states[stateIndex] == state || states[stateIndex] == ~state) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ColorStateList{" +
               "mThemeAttrs=" + Arrays.deepToString(mThemeAttrs) +
               "mChangingConfigurations=" + mChangingConfigurations +
               "mStateSpecs=" + Arrays.deepToString(mStateSpecs) +
               "mColors=" + Arrays.toString(mColors) +
               "mDefaultColor=" + mDefaultColor + '}';
    }

    /**
     * ok>>
     * Updates the default color and opacity.
     */
    //初始化各个颜色和状态等字段信息
    //<?xml version="1.0" encoding="utf-8"?>
    //<selector xmlns:android="http://schemas.android.com/apk/res/android">
    //    <item android:state_pressed="true"
    //        android:color="#ffff0000"/> <!-- pressed -->
    //    <item android:state_focused="true"
    //        android:color="#ff0000ff"/> <!-- focused -->
    //    <item android:color="#ff000000"/> <!-- default -->
    //</selector>
    private void onColorsChanged() {
        int defaultColor = DEFAULT_COLOR;
        boolean isOpaque = true;

        final int[][] states = mStateSpecs;
        final int[] colors = mColors;
        final int N = states.length;
        if (N > 0) {
            //默认颜色默认选择color数组中第一项
            defaultColor = colors[0];

            //选择默认颜色
            //把没有state属性的item对应的颜色值设置为默认颜色
            for (int i = N - 1; i > 0; i--) {
                if (states[i].length == 0) {
                    defaultColor = colors[i];
                    break;
                }
            }

            for (int i = 0; i < N; i++) {
                if (Color.alpha(colors[i]) != 0xFF) {
                    isOpaque = false;
                    break;
                }
            }
        }

        mDefaultColor = defaultColor;
        mIsOpaque = isOpaque;
    }

    /**
     * @return a factory that can create new instances of this ColorStateList
     * @hide only for resource preloading
     */
    public ConstantState<ComplexColor> getConstantState() {
        if (mFactory == null) {
            mFactory = new ColorStateListFactory(this);
        }
        return mFactory;
    }

    private static class ColorStateListFactory extends ConstantState<ComplexColor> {
        private final ColorStateList mSrc;

        public ColorStateListFactory(ColorStateList src) {
            mSrc = src;
        }

        @Override
        public @Config int getChangingConfigurations() {
            return mSrc.mChangingConfigurations;
        }

        @Override
        public ColorStateList newInstance() {
            return mSrc;
        }

        @Override
        public ColorStateList newInstance(Resources res, Theme theme) {
            return (ColorStateList) mSrc.obtainForTheme(theme);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (canApplyTheme()) {
            Log.w(TAG, "Wrote partially-resolved ColorStateList to parcel!");
        }
        final int N = mStateSpecs.length;
        dest.writeInt(N);
        for (int i = 0; i < N; i++) {
            dest.writeIntArray(mStateSpecs[i]);
        }
        dest.writeIntArray(mColors);
    }

    public static final Parcelable.Creator<ColorStateList> CREATOR =
            new Parcelable.Creator<ColorStateList>() {
        @Override
        public ColorStateList[] newArray(int size) {
            return new ColorStateList[size];
        }

        @Override
        public ColorStateList createFromParcel(Parcel source) {
            final int N = source.readInt();
            final int[][] stateSpecs = new int[N][];
            for (int i = 0; i < N; i++) {
                stateSpecs[i] = source.createIntArray();
            }
            final int[] colors = source.createIntArray();
            return new ColorStateList(stateSpecs, colors);
        }
    };
}
