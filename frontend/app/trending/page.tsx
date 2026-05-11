'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getTrending } from '../../lib/api';

interface TrendingPost {
  postId: number;
  content: string;
  authorUsername: string;
  likeCount: number;
  score: number;
}

export default function TrendingPage() {
  const router = useRouter();
  const [posts, setPosts] = useState<TrendingPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchTrending = useCallback(async () => {
    try {
      const data = await getTrending();
      setPosts(data);
    } catch {
      setError('Failed to load trending posts.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    fetchTrending();
  }, [router, fetchTrending]);

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
          <button onClick={() => router.push('/profile')} className="text-sm text-gray-400 hover:text-white transition">Profile</button>
          <button onClick={() => { localStorage.removeItem('token'); router.push('/login'); }} className="text-sm text-gray-400 hover:text-white transition">Logout</button>
        </div>
      </nav>

      <div className="max-w-2xl mx-auto px-4 py-6 space-y-4">
        {/* Header */}
        <div className="flex items-center gap-2 mb-2">
          <span className="text-2xl">🔥</span>
          <h2 className="text-white text-xl font-bold">Trending Posts</h2>
          <span className="ml-2 text-xs text-gray-500 bg-gray-800 px-2 py-1 rounded-full">Powered by Redis</span>
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg text-sm">
            {error}
          </div>
        )}

        {posts.length === 0 && !error ? (
          <div className="text-center text-gray-500 py-12">
            No trending posts yet. Like some posts to get them trending!
          </div>
        ) : (
          posts.map((post, index) => (
            <div key={post.postId} className="bg-gray-900 rounded-2xl p-5 border border-gray-800 relative">
              {/* Rank badge */}
              <div className={`absolute top-4 right-4 w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                ${index === 0 ? 'bg-yellow-500 text-black' : index === 1 ? 'bg-gray-400 text-black' : index === 2 ? 'bg-orange-600 text-white' : 'bg-gray-700 text-gray-300'}`}>
                #{index + 1}
              </div>

              <div className="flex items-center gap-3 mb-3 pr-10">
                <div className="w-9 h-9 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-bold">
                  {post.authorUsername?.[0]?.toUpperCase() || 'U'}
                </div>
                <p className="text-white text-sm font-semibold">{post.authorUsername || 'Unknown'}</p>
              </div>

              <p className="text-gray-300 text-sm leading-relaxed pr-10">{post.content}</p>

              <div className="mt-3 flex items-center gap-4">
                <span className="text-red-400 text-sm font-medium">♥ {post.likeCount || post.score || 0} likes</span>
                <span className="text-gray-600 text-xs">score: {post.score}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}