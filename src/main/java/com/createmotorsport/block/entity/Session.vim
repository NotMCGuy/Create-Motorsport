let SessionLoad = 1
let s:so_save = &g:so | let s:siso_save = &g:siso | setg so=0 siso=0 | setl so=-1 siso=-1
let v:this_session=expand("<sfile>:p")
doautoall SessionLoadPre
silent only
silent tabonly
cd ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity
if expand('%') == '' && !&modified && line('$') <= 1 && getline(1) == ''
  let s:wipebuf = bufnr('%')
endif
let s:shortmess_save = &shortmess
set shortmess+=aoO
badd +145 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/DownFlapBlock.java
badd +36 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/DownFlapBlockEntity.java
badd +63 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/client/SteeringWheelScreen.java
badd +333 ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java
argglobal
%argdel
edit ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java
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
wincmd =
argglobal
balt ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/DownFlapBlock.java
setlocal foldmethod=expr
setlocal foldexpr=v:lua.vim.lsp.foldexpr()
setlocal foldmarker={{{,}}}
setlocal foldignore=#
setlocal foldlevel=99
setlocal foldminlines=1
setlocal foldnestmax=20
setlocal foldenable
33
sil! normal! zo
53
sil! normal! zo
93
sil! normal! zo
154
sil! normal! zo
155
sil! normal! zo
163
sil! normal! zo
177
sil! normal! zo
196
sil! normal! zo
226
sil! normal! zo
230
sil! normal! zo
253
sil! normal! zo
263
sil! normal! zo
274
sil! normal! zo
278
sil! normal! zo
293
sil! normal! zo
327
sil! normal! zo
357
sil! normal! zo
358
sil! normal! zo
386
sil! normal! zo
387
sil! normal! zo
93
sil! normal! zo
154
sil! normal! zo
155
sil! normal! zo
163
sil! normal! zo
177
sil! normal! zo
196
sil! normal! zo
226
sil! normal! zo
230
sil! normal! zo
253
sil! normal! zo
263
sil! normal! zo
274
sil! normal! zo
278
sil! normal! zo
293
sil! normal! zo
327
sil! normal! zo
357
sil! normal! zo
358
sil! normal! zo
387
sil! normal! zo
388
sil! normal! zo
let s:l = 333 - ((12 * winheight(0) + 33) / 66)
if s:l < 1 | let s:l = 1 | endif
keepjumps exe s:l
normal! zt
keepjumps 333
normal! 0
wincmd w
argglobal
if bufexists(fnamemodify("~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java", ":p")) | buffer ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java | else | edit ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java | endif
if &buftype ==# 'terminal'
  silent file ~/Documents/Projects/Create-Motorsport/src/main/java/com/createmotorsport/block/entity/SteeringWheelBlockEntity.java
endif
setlocal foldmethod=expr
setlocal foldexpr=v:lua.vim.lsp.foldexpr()
setlocal foldmarker={{{,}}}
setlocal foldignore=#
setlocal foldlevel=99
setlocal foldminlines=1
setlocal foldnestmax=20
setlocal foldenable
33
sil! normal! zo
53
sil! normal! zo
93
sil! normal! zo
154
sil! normal! zo
155
sil! normal! zo
163
sil! normal! zo
177
sil! normal! zo
196
sil! normal! zo
226
sil! normal! zo
230
sil! normal! zo
253
sil! normal! zo
263
sil! normal! zo
274
sil! normal! zo
278
sil! normal! zo
293
sil! normal! zo
327
sil! normal! zo
357
sil! normal! zo
358
sil! normal! zo
386
sil! normal! zo
387
sil! normal! zo
93
sil! normal! zo
154
sil! normal! zo
155
sil! normal! zo
163
sil! normal! zo
177
sil! normal! zo
196
sil! normal! zo
226
sil! normal! zo
230
sil! normal! zo
253
sil! normal! zo
263
sil! normal! zo
274
sil! normal! zo
278
sil! normal! zo
293
sil! normal! zo
327
sil! normal! zo
357
sil! normal! zo
358
sil! normal! zo
387
sil! normal! zo
388
sil! normal! zo
482
sil! normal! zo
498
sil! normal! zo
560
sil! normal! zo
let s:l = 520 - ((49 * winheight(0) + 33) / 66)
if s:l < 1 | let s:l = 1 | endif
keepjumps exe s:l
normal! zt
keepjumps 520
normal! 085|
wincmd w
wincmd =
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
