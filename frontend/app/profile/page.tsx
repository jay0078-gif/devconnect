'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getMe, getMyFeed } from '../../lib/api';

interface User {
  id: number;
  username: string;
  email: string;
  bio: string;
}

interface Post {
  id: number;
  content: string;
  createdAt: string;
  likeCount: number;
}

export default function ProfilePage() {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      const [userData, feedData] = await Promise.all([getMe(), getMyFeed()]);
      setUser(userData);
      setPosts(feedData.content || feedData);
    } catch {
      router.push('/login');
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    fetchData();
  }, [router, fetchData]);

  if (loading) return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="text-gray-400">Loading...</div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-950">
      {/* Navbar */}
      <nav className="bg-gray-900 border-b border-gray-800 px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <h1 className="text-xl font-bold text-white cursor-pointer" onClick={() => router.push('/feed')}>
          Dev<span className="text-blue-500">Connect</span>
        </h1>
        <div className="flex items-center gap-4">
          <button onClick={() => router.push('/feed')} className="text-sm text-gray-400 hover:text-white transition">Feed</button>
          <button onClick={() => router.push('/trending')} className="text-sm text-gray-400 hover:text-white transition">Trending</button>
          <button onClick={() => { localStorage.removeItem('token'); router.push('/login'); }} className="text-sm text-gray-400 hover:text-white transition">Logout</button>
        </div>
      </nav>

      <div className="max-w-2xl mx-auto px-4 py-6 space-y-4">
        {/* Profile Card */}
        {user && (
          <div className="bg-gray-900 rounded-2xl p-6 border border-gray-800">
            <div className="flex items-center gap-4">
              <div className="w-16 h-16 rounded-full bg-blue-600 flex items-center justify-center text-white text-2xl font-bold">
                {user.username?.[0]?.toUpperCase()}
              </div>
              <div>
                <h2 className="text-white text-xl font-bold">{user.username}</h2>
                <p className="text-gray-400 text-sm">{user.email}</p>
                {user.bio && <p className="text-gray-300 text-sm mt-1">{user.bio}</p>}
              </div>
            </div>
            <div className="mt-4 pt-4 border-t border-gray-800 flex gap-6">
              <div className="text-center">
                <p className="text-white font-bold text-lg">{posts.length}</p>
                <p className="text-gray-500 text-xs">Posts</p>
              </div>
            </div>
          </div>
        )}

        {/* My Posts */}
        <h3 className="text-white font-semibold text-lg">My Posts</h3>

        {posts.length === 0 ? (
          <div className="text-center text-gray-500 py-12">You haven't posted anything yet.</div>
        ) : (
          posts.map((post) => (
            <div key={post.id} className="bg-gray-900 rounded-2xl p-5 border border-gray-800">
              <p className="text-gray-300 text-sm leading-relaxed">{post.content}</p>
              <div className="mt-3 flex items-center justify-between">
                <span className="text-gray-500 text-xs">{new Date(post.createdAt).toLocaleDateString()}</span>
                <span className="text-gray-500 text-sm">♥ {post.likeCount || 0}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}