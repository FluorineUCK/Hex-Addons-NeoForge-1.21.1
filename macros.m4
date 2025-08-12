divert(-1)
define(shr, >>) define(shl, <<) define(Shr, >>)
changequote(<<,>>) changecom(/**/)
define(pyeval,<<esyscmd(python3 -c 'print(patsubst($1,','\''), end="")')>>)
define(opencom,/*) define(closecom,*/)
define(ifversion, <<ifelse(eval(minecraft_version <<$1>>),1,<<$2>>,<<$3>>)>>)
define(<<forloop>>, <<pushdef(<<$1>>, <<$2>>)_$0($@)popdef(<<$1>>)>>)
define(<<_forloop>>, <<$4<<>>ifelse($1, <<$3>>, <<>>, <<define(<<$1>>, incr($1))$0($@)>>)>>))
ifelse(SEED,1,<<divert<<>>dnl
┌ [1;31mATTENTION:[0m ──────────────────────────────────────────────────┐
│ Seed mode is enabled. Seed mode should only be used once!    │
│ Remember to remove --seed before compiling for distribution. │
└──────────────────────────────────────────────────────────────┘
divert(-1)define(seeded,<<divert(-1)>>)>>,<<define(seeded,divert)>>)
divert