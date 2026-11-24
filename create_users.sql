USE `wishes_sender`;

INSERT INTO `users` (`id`, `name`, `email`, `password_hash`, `role`, `ai_enabled`, `share_details`, `remind_days_before`, `created_at`) VALUES
(100, 'Arif Hossain',     'arif.hossain@example.com',     '$2a$10$/7p7omSqBIva6RDb5KXDrOvFA8sZiXl0ZQzHT7dKGK93BruV03LSm', 'ADMIN', 1, 1, 1,  '2026-01-05 09:15:00'),
(101, 'Nusrat Jahan',     'nusrat.jahan@example.com',     '$2a$10$e3/tQOgIgHZvncfQnWvyR..IgQTZz0jUFY7N7jaJJTdEBxmEMgUbu', 'ADMIN', 1, 0, 2,  '2026-01-12 11:40:00'),
(102, 'Tanvir Ahmed',     'tanvir.ahmed@example.com',     '$2a$10$O50ms2p6K0ODaKt02G.QUOZZlZm8BjUQof3FkBZAYv0WaRN5u1Mx.', 'USER',  1, 1, 1,  '2026-02-03 08:20:00'),
(103, 'Farhana Akter',    'farhana.akter@example.com',    '$2a$10$4dJ5Qq5YddiUUSG9dyvrZOWXb/VnS8M7H979wV7v.Vh0eHa8kSpIG', 'USER',  0, 1, 3,  '2026-02-14 19:05:00'),
(104, 'Rakib Hasan',      'rakib.hasan@example.com',      '$2a$10$SgeGegLIhw5wnWOXfE41U.5nilVzniOpdtdbhBMfu9D59YLHpzIiy', 'USER',  1, 0, 0,  '2026-02-28 13:30:00'),
(105, 'Sadia Islam',      'sadia.islam@example.com',      '$2a$10$Uw.rGOiy/2NOgdR6uH1/3eh/tHkkVbUpsXy4zaXOeUbEQwdURKfUm', 'USER',  1, 1, 7,  '2026-03-10 10:00:00'),
(106, 'Mehedi Hasan',     'mehedi.hasan@example.com',     '$2a$10$t4XdggSqaDnEaWviQQ4us.6T1TcD/YcUxZTG0nHe69LTrl5N7bGri', 'USER',  0, 0, 1,  '2026-03-22 16:45:00'),
(107, 'Tasnim Rahman',    'tasnim.rahman@example.com',    '$2a$10$b..tQE7Dir/O2ma4n0Mik.wzEgluSTRwoTvPKCFza05wgK8SwnqJm', 'USER',  1, 1, 2,  '2026-04-02 07:55:00'),
(108, 'Sourav Das',       'sourav.das@example.com',       '$2a$10$CrOKLPaGKDghNzFTZuSMn.UxTxQqx0d..kGh1KxDZlaGciijzhkQC', 'USER',  1, 1, 5,  '2026-04-18 21:10:00'),
(109, 'Priya Saha',       'priya.saha@example.com',       '$2a$10$0123rzoFPfWMleqz244vFOALf/hYSNWM27/PlMnt4j2UuNYG1Mc5.', 'USER',  0, 1, 1,  '2026-05-01 12:25:00'),
(110, 'Anik Barua',       'anik.barua@example.com',       '$2a$10$rDPVbAGOjGKkhlHM2Yn5X.Oerkq6xkmwmVoHKwNS7Wff43dUcxSIS', 'USER',  1, 0, 14, '2026-05-20 09:35:00'),
(111, 'Moumita Chakma',   'moumita.chakma@example.com',   '$2a$10$LdJuOV2uOd5wgMwTab0zT.OnsObYX1Cx..jY0/bPrp5xxT6D4zTHS', 'USER',  1, 1, 3,  '2026-06-08 18:00:00'),
(112, 'Joseph Gomes',     'joseph.gomes@example.com',     '$2a$10$Y/4bIiQHr7yEd7p8Am/TWuNaN3dMWZKj/w/BAD5FkWciqaJzYe1.u', 'USER',  0, 0, 30, '2026-06-25 14:15:00'),
(113, 'Maria Rozario',    'maria.rozario@example.com',    '$2a$10$Fi8e1cZVSUTso1e2DP8s4OSUzEt/RkHc5Dy.4cWIKXdGqvMq7mNTa', 'USER',  1, 1, 1,  '2026-07-09 10:50:00'),
(114, 'Imran Chowdhury',  'imran.chowdhury@example.com',  '$2a$10$qq1kPd0bPnfOfY7jFqxM0.0us0NlKuZ74yGDrrRF0QjDBdqo4aCOm', 'USER',  1, 1, 10, '2026-07-30 20:20:00'),
(115, 'Lamia Sultana',    'lamia.sultana@example.com',    '$2a$10$vgG3fR/ZtHDl9TtY/B3lkOR3I.WErLTACa6ApiDir3l1/gxIQKswG', 'USER',  0, 1, 2,  '2026-08-15 08:05:00'),
(116, 'Rafiq Uddin',      'rafiq.uddin@example.com',      '$2a$10$ddu5RLbx4Y8hG5lLvf3E3OX.znruIH1aUKK6JCSvwdm830CYSuPBK', 'USER',  1, 0, 1,  '2026-09-03 15:40:00'),
(117, 'Shirin Akhter',    'shirin.akhter@example.com',    '$2a$10$1fiL1VqJEwMR1cUFTUppWO.oa2dD.CMv38a.JN6hmQtfPnxebWNQi', 'USER',  1, 1, 0,  '2026-09-27 11:00:00'),
(118, 'Debashis Roy',     'debashis.roy@example.com',     '$2a$10$dF7c587VUpQSF6zbUhyrN.HPD/7mlpH2q6Hwt3UlavO8cD/EJieTG', 'USER',  0, 0, 21, '2026-10-19 17:30:00'),
(119, 'Ayesha Siddiqua',  'ayesha.siddiqua@example.com',  '$2a$10$jiDxXf9v8FOffped7HFcye8GALTxL87f0GAWQPxlAf1pabWnQP6mK', 'USER',  1, 1, 4,  '2026-11-10 09:00:00');
