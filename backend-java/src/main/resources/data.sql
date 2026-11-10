-- 观影中心 (MovieHub) MySQL 初始默认片源数据 (INSERT IGNORE 防止重复插入)

INSERT IGNORE INTO app_meta (meta_key, meta_value) VALUES ('bundle_version', '2.0.0');

INSERT IGNORE INTO vod_source (source_key, name, api_url, enabled, sort_order, latency_ms, health_status) VALUES
('ffzy',     '非凡',   'http://ffzy5.tv/api.php/provide/vod/',         1, 10, 0, 'ONLINE'),
('bfzy',     '暴风',   'https://bfzyapi.com/api.php/provide/vod/',     1, 20, 0, 'ONLINE'),
('lzi',      '量子',   'https://cj.lziapi.com/api.php/provide/vod/',   1, 30, 0, 'ONLINE'),
('tyyszy',   '天涯',   'https://tyyszy.com/api.php/provide/vod/',      1, 40, 0, 'ONLINE'),
('zy360',    '360',    'https://360zy.com/api.php/provide/vod/',       1, 50, 0, 'ONLINE'),
('wolong',   '卧龙',   'https://wolongzyw.com/api.php/provide/vod/',   1, 60, 0, 'ONLINE'),
('jisu',     '极速',   'https://jszyapi.com/api.php/provide/vod/',     1, 70, 0, 'ONLINE'),
('dbzy',     '豆瓣源', 'https://dbzy.tv/api.php/provide/vod/',         1, 80, 0, 'ONLINE'),
('mozhuazy', '魔爪',   'https://mozhuazy.com/api.php/provide/vod/',    1, 90, 0, 'ONLINE'),
('mdzy',     '魔都',   'https://www.mdzyapi.com/api.php/provide/vod/', 1, 100, 0, 'ONLINE');

INSERT IGNORE INTO live_channel (channel_code, name, group_name, stream_url, enabled, sort_order) VALUES
('cctv1',    'CCTV-1 综合',     '央视频道', 'https://live-play.cctvnews.cctv.com/cctv/merge_cctv1.m3u8', 1, 10),
('cctv2',    'CCTV-2 财经',     '央视频道', 'https://live-play.cctvnews.cctv.com/cctv/merge_cctv2.m3u8', 1, 20),
('cctv4',    'CCTV-4 中文国际', '央视频道', 'https://live-play.cctvnews.cctv.com/cctv/merge_cctv4.m3u8', 1, 30),
('cctv13',   'CCTV-13 新闻',    '央视频道', 'https://live-play.cctvnews.cctv.com/cctv/merge_cctv13.m3u8', 1, 40),
('cgtn',     'CGTN 国际英语',   '国际频道', 'https://news.cgtn.com/resource/live/english/cgtn-news.m3u8', 1, 50),
('cgtn_doc', 'CGTN 纪录',       '国际频道', 'https://news.cgtn.com/resource/live/document/cgtn-doc.m3u8', 1, 60);

INSERT IGNORE INTO music_script (script_key, name, script_url, enabled, sort_order) VALUES
('huibq',              'Huibq 音源',       'https://fastly.jsdelivr.net/gh/Huibq/keep-alive/render_api.js', 1, 10),
('lx_official_mirror', '洛雪聚合镜像源',   'https://fastly.jsdelivr.net/gh/pdone/lx-music-source/sixyin/latest.js', 1, 20),
('ikun_source',        'iKun 公益无损源', 'https://fastly.jsdelivr.net/gh/ikun0014/lx-music-source/dist/lx-music-source.js', 1, 30);
