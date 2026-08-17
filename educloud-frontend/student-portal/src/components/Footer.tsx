import { Link } from 'react-router-dom';
import { GraduationCap, Mail, Phone, MapPin } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="bg-indigo-900 text-paper/70 mt-20">
      <div className="max-w-7xl mx-auto px-4 md:px-8 py-14">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-10">
          {/* Brand */}
          <div className="lg:col-span-1">
            <div className="flex items-center gap-2.5 mb-4">
              <div className="w-9 h-9 bg-amber-600 flex items-center justify-center">
                <GraduationCap size={20} className="text-white" />
              </div>
              <div>
                <div className="font-display text-lg font-700 text-paper leading-none">EduCloud</div>
                <div className="text-[10px] text-paper/40 tracking-widest uppercase mt-0.5">Education Cloud</div>
              </div>
            </div>
            <p className="text-sm leading-relaxed mb-4">
              专注于高品质在线教育的学习平台，汇聚顶尖师资，为每一位学习者提供卓越的学习体验。
            </p>
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2"><Mail size={14} className="text-amber-400" /> support@educloud.cn</div>
              <div className="flex items-center gap-2"><Phone size={14} className="text-amber-400" /> 400-888-0000</div>
              <div className="flex items-center gap-2"><MapPin size={14} className="text-amber-400" /> 北京市海淀区中关村</div>
            </div>
          </div>

          {/* Links */}
          <div>
            <h4 className="font-display text-sm font-600 text-paper uppercase tracking-wider mb-4">课程分类</h4>
            <ul className="space-y-2.5 text-sm">
              {['计算机', '数学', '语言学习', '经济管理', '设计', '心理学'].map((c) => (
                <li key={c}>
                  <Link to={`/courses?category=${encodeURIComponent(c)}`} className="hover:text-amber-400 transition-colors">
                    {c}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-display text-sm font-600 text-paper uppercase tracking-wider mb-4">学习支持</h4>
            <ul className="space-y-2.5 text-sm">
              <li><Link to="/my-courses" className="hover:text-amber-400 transition-colors">我的课程</Link></li>
                <li><Link to="/live/1" className="hover:text-amber-400 transition-colors">直播课堂</Link></li>
              <li><Link to="/assignments" className="hover:text-amber-400 transition-colors">作业中心</Link></li>
              <li><Link to="/exams" className="hover:text-amber-400 transition-colors">考试中心</Link></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">常见问题</a></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">学习指南</a></li>
            </ul>
          </div>

          <div>
            <h4 className="font-display text-sm font-600 text-paper uppercase tracking-wider mb-4">关于我们</h4>
            <ul className="space-y-2.5 text-sm">
              <li><a href="#" className="hover:text-amber-400 transition-colors">平台介绍</a></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">加入我们</a></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">合作伙伴</a></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">用户协议</a></li>
              <li><a href="#" className="hover:text-amber-400 transition-colors">隐私政策</a></li>
            </ul>
          </div>
        </div>

        <div className="border-t border-paper/10 mt-10 pt-6 flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-paper/40">
          <div>© 2024 EduCloud 教育云平台. 京ICP备2024000000号-1</div>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-amber-400 transition-colors">微信公众号</a>
            <a href="#" className="hover:text-amber-400 transition-colors">微博</a>
            <a href="#" className="hover:text-amber-400 transition-colors">知乎</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
