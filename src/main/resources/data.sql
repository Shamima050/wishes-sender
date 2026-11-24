-- =========================================================
--  Seed data (INSERT IGNORE + fixed ids = safe to re-run)
--  The admin user is created by DataSeeder.java (needs BCrypt).
-- =========================================================

-- Event types (lookup table used by events, gifts and wish_templates)
INSERT IGNORE INTO event_types (code, label, sort_order) VALUES
('BIRTHDAY',    'Birthday',    1),
('ANNIVERSARY', 'Anniversary', 2),
('FESTIVAL',    'Festival',    3),
('CUSTOM',      'Custom',      4);

-- Festivals (lunar festival dates are approximate - admin can edit them)
INSERT IGNORE INTO festivals (id, name, festival_date, religion, region) VALUES
(1,  'Victory Day',                 '2026-12-16', 'All',       'Bangladesh'),
(2,  'Christmas',                   '2026-12-25', 'Christian', 'Global'),
(3,  'New Year',                    '2027-01-01', 'All',       'Global'),
(4,  'Valentine''s Day',            '2027-02-14', 'All',       'Global'),
(5,  'International Mother Language Day', '2027-02-21', 'All', 'Bangladesh'),
(6,  'Eid al-Fitr',                 '2027-03-10', 'Islam',     'Global'),
(7,  'Easter',                      '2027-03-28', 'Christian', 'Global'),
(8,  'Pohela Boishakh',             '2027-04-14', 'All',       'Bangladesh'),
(9,  'Eid al-Adha',                 '2027-05-17', 'Islam',     'Global'),
(10, 'Buddha Purnima',              '2027-05-20', 'Buddhism',  'Global'),
(11, 'Durga Puja',                  '2027-10-15', 'Hinduism',  'Bangladesh'),
(12, 'Diwali',                      '2027-10-29', 'Hinduism',  'Global');

-- Wish templates. {name} is replaced by the connection's name.
INSERT IGNORE INTO wish_templates (id, event_type, tone, text) VALUES
(1,  'BIRTHDAY',    'Formal',    'Dear {name}, wishing you a very happy birthday and a successful year ahead.'),
(2,  'BIRTHDAY',    'Emotional', 'Happy birthday, {name}! You make my life brighter every single day. Stay happy always.'),
(3,  'BIRTHDAY',    'Funny',     'Happy birthday, {name}! Don''t worry about getting older - you are still younger than you will be next year!'),
(4,  'BIRTHDAY',    'Romantic',  'Happy birthday, my love {name}. Every year with you is my favourite gift.'),
(5,  'ANNIVERSARY', 'Formal',    'Dear {name}, warm congratulations on your anniversary. Wishing you many more happy years.'),
(6,  'ANNIVERSARY', 'Emotional', 'Happy anniversary, {name}! Your love story inspires everyone around you.'),
(7,  'ANNIVERSARY', 'Funny',     'Happy anniversary, {name}! Another year of putting up with each other - true heroes!'),
(8,  'ANNIVERSARY', 'Romantic',  'Happy anniversary, {name}. I would choose you again in every lifetime.'),
(9,  'FESTIVAL',    'Formal',    'Dear {name}, wishing you and your family a joyful and blessed festival.'),
(10, 'FESTIVAL',    'Emotional', 'Happy festival, {name}! May this day fill your home with love, peace and happiness.'),
(11, 'FESTIVAL',    'Funny',     'Happy festival, {name}! Eat well, sleep well, and save some sweets for me!'),
(12, 'CUSTOM',      'Formal',    'Dear {name}, best wishes to you on this special day.'),
(13, 'CUSTOM',      'Emotional', '{name}, thinking of you today and wishing you all the happiness in the world.');

-- Gift catalog (prices in BDT). event_type NULL = suits any event (the API shows it as "ANY")
INSERT IGNORE INTO gifts (id, name, category, price, event_type, tag, reason) VALUES
(1,  'Bestselling novel',          'Books',       600,  NULL,          'books',       'A good story is a gift they can enjoy again and again.'),
(2,  'Bluetooth speaker',          'Electronics', 2500, NULL,          'music',       'Perfect for someone who always has music playing.'),
(3,  'Personalised coffee mug',    'Home',        450,  'BIRTHDAY',    'coffee',      'A daily reminder of you with every cup.'),
(4,  'Chocolate gift box',         'Food',        900,  NULL,          'sweets',      'Sweet, safe and loved by almost everyone.'),
(5,  'Scented candle set',         'Home',        1200, NULL,          'home',        'Creates a calm, cozy mood at home.'),
(6,  'Fitness smart band',         'Electronics', 3500, 'BIRTHDAY',    'fitness',     'Helps them track steps and stay active.'),
(7,  'Couple photo frame',         'Home',        800,  'ANNIVERSARY', 'photos',      'Keeps your best memory on display.'),
(8,  'Flower bouquet',             'Flowers',     1000, NULL,          'flowers',     'A classic way to say you care.'),
(9,  'Traditional saree / panjabi','Clothing',    3000, 'FESTIVAL',    'fashion',     'Perfect to wear on the festival day.'),
(10, 'Cooking class voucher',      'Experience',  2000, NULL,          'cooking',     'For someone who loves trying new dishes.'),
(11, 'Indoor plant',               'Home',        700,  NULL,          'plants',      'A living gift that grows with them.'),
(12, 'Sweets box (mishti)',        'Food',        800,  'FESTIVAL',    'sweets',      'Festivals are better with mishti!'),
(13, 'Wireless earbuds',           'Electronics', 4000, NULL,          'music',       'Great for music lovers on the move.'),
(14, 'Travel backpack',            'Travel',      2800, NULL,          'travel',      'Useful for their next trip.'),
(15, 'Dinner for two voucher',     'Experience',  4500, 'ANNIVERSARY', 'food',        'A special evening to celebrate together.'),
(16, 'Sketchbook and pencils set', 'Art',         900,  NULL,          'art',         'Lets their creativity flow.'),
(17, 'Football',                   'Sports',      1500, NULL,          'sports',      'For the sports fan who loves to play.'),
(18, 'Desk organiser',             'Office',      700,  NULL,          'work',        'A useful, professional gift for colleagues and clients.');
