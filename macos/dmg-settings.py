# dmgbuild settings for build-dmg.sh (defines: app, background, icon)
import os.path

app = defines["app"]
files = [app]
symlinks = {"Applications": "/Applications"}
icon = defines["icon"]
background = defines["background"]  # background.png (+ background@2x.png next to it for Retina)

format = "UDZO"
filesystem = "HFS+"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
window_rect = ((200, 160), (660, 400))
default_view = "icon-view"
icon_size = 128
text_size = 13
arrange_by = None
icon_locations = {
    os.path.basename(app): (140, 170),
    "Applications": (475, 170),
    # Finders that show hidden files would draw these inside the window: park them outside it
    ".background.tiff": (170, 700),
    ".VolumeIcon.icns": (490, 700),
}
