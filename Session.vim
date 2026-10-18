let SessionLoad = 1
let s:so_save = &g:so | let s:siso_save = &g:siso | setg so=0 siso=0 | setl so=-1 siso=-1
let v:this_session=expand("<sfile>:p")
doautoall SessionLoadPre
silent only
silent tabonly
cd ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block
if expand('%') == '' && !&modified && line('$') <= 1 && getline(1) == ''
  let s:wipebuf = bufnr('%')
endif
let s:shortmess_save = &shortmess
set shortmess+=aoO
badd +39 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/DownFlapBlock.java
badd +85 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/DownFlapBlockEntity.java
badd +18 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/client/SteeringWheelScreen.java
badd +354 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java
badd +29 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SuspensionBlockEntity.java
argglobal
%argdel
edit ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/DownFlapBlock.java
let s:save_splitbelow = &splitbelow
let s:save_splitright = &splitright
set splitbelow splitright
wincmd _ | wincmd |
vsplit
1wincmd h
wincmd w
let &splitbelow = s:save_splitbelow
let &splitright = s:save_splitright
wincmd t
let s:save_winminheight = &winminheight
let s:save_winminwidth = &winminwidth
set winminheight=0
set winheight=1
set winminwidth=0
set winwidth=1
exe 'vert 1resize ' . ((&columns * 172 + 172) / 344)
exe 'vert 2resize ' . ((&columns * 171 + 172) / 344)
argglobal
balt ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SuspensionBlockEntity.java
setlocal foldmethod=expr
setlocal foldexpr=v:lua.vim.lsp.foldexpr()
setlocal foldmarker={{{,}}}
setlocal foldignore=#
setlocal foldlevel=99
setlocal foldminlines=1
setlocal foldnestmax=20
setlocal foldenable
let s:l = 39 - ((14 * winheight(0) + 41) / 82)
if s:l < 1 | let s:l = 1 | endif
keepjumps exe s:l
normal! zt
keepjumps 39
normal! 061|
wincmd w
argglobal
if bufexists(fnamemodify("~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java", ":p")) | buffer ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java | else | edit ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java | endif
if &buftype ==# 'terminal'
  silent file ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java
endif
balt ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SuspensionBlockEntity.java
setlocal foldmethod=expr
setlocal foldexpr=v:lua.vim.lsp.foldexpr()
setlocal foldmarker={{{,}}}
setlocal foldignore=#
setlocal foldlevel=99
setlocal foldminlines=1
setlocal foldnestmax=20
setlocal foldenable
let s:l = 354 - ((40 * winheight(0) + 41) / 82)
if s:l < 1 | let s:l = 1 | endif
keepjumps exe s:l
normal! zt
keepjumps 354
normal! 0
wincmd w
exe 'vert 1resize ' . ((&columns * 172 + 172) / 344)
exe 'vert 2resize ' . ((&columns * 171 + 172) / 344)
tabnext 1
if exists('s:wipebuf') && len(win_findbuf(s:wipebuf)) == 0 && getbufvar(s:wipebuf, '&buftype') isnot# 'terminal'
  silent exe 'bwipe ' . s:wipebuf
endif
unlet! s:wipebuf
set winheight=1 winwidth=20
let &shortmess = s:shortmess_save
let &winminheight = s:save_winminheight
let &winminwidth = s:save_winminwidth
let s:sx = expand("<sfile>:p:r")."x.vim"
if filereadable(s:sx)
  exe "source " . fnameescape(s:sx)
endif
let &g:so = s:so_save | let &g:siso = s:siso_save
set hlsearch
nohlsearch
doautoall SessionLoadPost
unlet SessionLoad
" vim: set ft=vim :
