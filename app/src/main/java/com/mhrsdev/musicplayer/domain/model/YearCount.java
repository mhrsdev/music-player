package com.mhrsdev.musicplayer.domain.model;

/** One entry of the Years view: a release year (never 0) and how many library tracks carry it. */
public record YearCount(int year, int trackCount) { }
