from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
CYAN=(0,229,255,255); LIGHT=(232,236,241,255); BG=(7,17,26,255)
FONT='/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf'
S=4  # süper örnekleme

def rounded_triangle(d, cx, cy, r, fill, rad):
    # sağa bakan eşkenar üçgen, köşeleri yuvarlatılmış (köşe çemberlerinin dışbükey zarfı)
    pts=[(cx+r*math.cos(math.radians(a)), cy+r*math.sin(math.radians(a))) for a in (0,120,240)]
    # köşeleri içeri çek
    inner=[(cx+(x-cx)*(1-rad/r*2), cy+(y-cy)*(1-rad/r*2)) for x,y in pts]
    # zarf: köşe çemberleri + aralarını dolduran çokgen
    import itertools
    poly=[]
    for i,(x,y) in enumerate(inner):
        a0=[0,120,240][i]
        for k in range(-60,61,5):
            a=math.radians(a0+k); poly.append((x+rad*2*math.cos(a), y+rad*2*math.sin(a)))
    d.polygon(poly, fill=fill)

def mark(size, bg=None):
    """Hilal (karanlık) + içinde oynat üçgeni. size: kare kenar (px)."""
    W=size*S
    im=Image.new('RGBA',(W,W),bg or (0,0,0,0))
    m=Image.new('L',(W,W),0); md=ImageDraw.Draw(m)
    R=W*0.46; c=W/2
    md.ellipse([c-R,c-R,c+R,c+R],fill=255)
    # hilal: sağ üstten kaydırılmış çemberle oyulur
    r2=R*0.86; ox=c+R*0.30; oy=c-R*0.22
    md.ellipse([ox-r2,oy-r2,ox+r2,oy+r2],fill=0)
    cres=Image.new('RGBA',(W,W),CYAN); im.paste(cres,(0,0),m)
    d=ImageDraw.Draw(im)
    rounded_triangle(d, ox-R*0.02, oy+R*0.04, R*0.50, LIGHT, R*0.035)
    return im.resize((size,size),Image.LANCZOS)

def wordmark_logo(w,h,bg=None,mark_scale=0.78,fill=0.90):
    W,H=w*S,h*S
    im=Image.new('RGBA',(W,H),bg or (0,0,0,0)); d=ImageDraw.Draw(im)
    f=1.0
    for _ in range(40):
        ms=int(h*mark_scale*f)
        font=ImageFont.truetype(FONT,int(H*0.30*f))
        gap=int(H*0.06*f)
        w1=d.textlength("Dark",font=font); sp=d.textlength(" ",font=font); w2=d.textlength("Play",font=font)
        total=ms*S+gap+w1+sp+w2
        if total<=W*fill: break
        f*=0.96
    mk=mark(ms).resize((ms*S,ms*S),Image.LANCZOS)
    x=(W-total)/2; y=(H-ms*S)/2
    im.alpha_composite(mk,(int(x),int(y)))
    tx=x+ms*S+gap
    bb=font.getbbox("DarkPlay")
    ty=H/2-(bb[1]+bb[3])/2
    d.text((tx,ty),"Dark",font=font,fill=LIGHT)
    d.text((tx+w1+sp,ty),"Play",font=font,fill=CYAN)
    return im.resize((w,h),Image.LANCZOS)

res='/home/claude/plato/app/src/main/res/'
# Launcher ön plan: 108dp, güvenli alan ortadaki 66dp → işaret ~%58
for name,px in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    can=Image.new('RGBA',(px,px),(0,0,0,0))
    ms=int(px*0.58); mk=mark(ms)
    can.alpha_composite(mk,((px-ms)//2,(px-ms)//2))
    can.save(res+f'mipmap-{name}/ic_launcher_foreground.png')
wordmark_logo(1232,576).save(res+'drawable-nodpi/logo.png')
wordmark_logo(640,360,bg=BG,mark_scale=0.40,fill=0.84).save(res+'drawable-xhdpi/tv_banner.png')
# önizleme
prev=Image.new('RGBA',(1400,800),BG)
prev.alpha_composite(wordmark_logo(1232,576),(84,0))
ic=Image.new('RGBA',(216,216),BG); ic.alpha_composite(Image.open(res+'mipmap-xhdpi/ic_launcher_foreground.png'))
mask=Image.new('L',(216,216),0); ImageDraw.Draw(mask).ellipse([0,0,215,215],fill=255)
prev.paste(ic,(100,560),mask)
prev.alpha_composite(Image.open(res+'drawable-xhdpi/tv_banner.png').resize((320,180)),(400,580))
prev.save('preview.png')
