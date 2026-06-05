from hexdoc.plugin import (
    HookReturn,
    ModPlugin,
    ModPluginImpl,
    ModPluginWithBook,
    hookimpl,
)
import hexdoc_hexic
from importlib.resources import Package
from typing_extensions import override
from hexdoc_hexcasting.book.page.pages import LookupPatternPage
from .__gradle_version__ import *
from .__version__ import *

LookupPatternPage._check_anchor.__code__ = (lambda self: self).__code__

class HexicModPlugin(ModPluginWithBook):
    modid = "hexic"
    full_version = FULL_VERSION
    mod_version = VERSION
    plugin_version = PY_VERSION
    def resource_dirs(self):
        from ._export import generated
        return generated
    def jinja_template_root(self):
        return hexdoc_hexic, "_templates"

class HexicPlugin(ModPluginImpl):
    @staticmethod
    @hookimpl
    def hexdoc_mod_plugin(branch: str) -> ModPlugin:
        return HexicModPlugin(branch=branch)