package com.mhrsdev.musicplayer.domain.model;

/**
 * How the UI lays out a {@link HomeSection}: HERO_CAROUSEL / CAROUSEL (cards), LIST (track rows), CONTINUE (one resume
 * card), AVATARS (round artist row), CHIPS (decade / genre / folder pills), ACTIONS (shuffle library + surprise me
 * buttons, no items), STATS (the "Your listening" card, data in {@link HomeSection#stats}).
 */
public enum HomeSectionStyle { HERO_CAROUSEL, CAROUSEL, LIST, CONTINUE, AVATARS, CHIPS, ACTIONS, STATS }
