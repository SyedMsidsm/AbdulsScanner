package com.syedmsidsm.abdulsscanner.domain.pdf

/**
 * PDF coordinate mapping.
 * Overlays use normalised top-left coordinates: x, y, w, h in 0..1.
 *
 * For a PDF page with media box width W and height H, and rotation r (in degrees: 0, 90, 180, 270):
 * In PDF user space, (0, 0) is at the bottom-left of the unrotated media box.
 */
object PdfCoordinates {

    data class PdfRect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    /**
     * Maps normalized overlay coordinates (0..1 top-left based) into PDF user space coordinates
     * taking into account page dimensions (W, H) and rotation (r = 0, 90, 180, 270).
     */
    fun mapToPdfSpace(
        normX: Float,
        normY: Float,
        normW: Float,
        normH: Float,
        pageWidth: Float,
        pageHeight: Float,
        rotation: Int
    ): PdfRect {
        val normRot = ((rotation % 360) + 360) % 360

        // First rotate the unit square point and size by -r to map from visual page space to unrotated media box space
        val (unrotX, unrotY, unrotW, unrotH) = when (normRot) {
            90 -> {
                // Visual top-left is at (1 - (normY + normH), normX) in unrotated space
                val ux = 1f - (normY + normH)
                val uy = normX
                val uw = normH
                val uh = normW
                listOf(ux, uy, uw, uh)
            }
            180 -> {
                // Visual top-left is at (1 - (normX + normW), 1 - (normY + normH))
                val ux = 1f - (normX + normW)
                val uy = 1f - (normY + normH)
                val uw = normW
                val uh = normH
                listOf(ux, uy, uw, uh)
            }
            270 -> {
                // Visual top-left is at (normY, 1 - (normX + normW))
                val ux = normY
                val uy = 1f - (normX + normW)
                val uw = normH
                val uh = normW
                listOf(ux, uy, uw, uh)
            }
            else -> {
                // 0 degrees
                listOf(normX, normY, normW, normH)
            }
        }

        // Apply r = 0 PDF formula:
        // px = unrotX * W
        // py = H - (unrotY + unrotH) * H
        // pw = unrotW * W
        // ph = unrotH * H
        val px = unrotX * pageWidth
        val py = pageHeight - (unrotY + unrotH) * pageHeight
        val pw = unrotW * pageWidth
        val ph = unrotH * pageHeight

        return PdfRect(px, py, pw, ph)
    }
}
