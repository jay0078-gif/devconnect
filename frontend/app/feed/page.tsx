'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getGlobalFeed, createPost, likePost } from '../../lib/api';

interface Post {
  id: number;
  content: string;
  authorUsername: string;
  createdAt: string;
  likeCount: number;
}

export default function FeedPage() {
  const router = useRouter();
  const [posts, setPosts] = useState<Post[]>([]);
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [posting, setPosting] = useState(false);

  const fetchPosts = useCallback(async () => {
    try {
      const data = await getGlobalFeed();
      setPosts(data.content || data);
    } catch {
      router.push('/login');
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    fetchPosts();
  }, [router, fetchPosts]);

  const handlePost = async () => {
    if (!content.trim()) return;
    setPosting(true);
    try {
      await createPost({ content });
      setContent('');
      fetchPosts();
    } catch {
      console.error('Post failed');
    } finally {
      setPosting(false);
    }
  };

  const handleLike = async (id: number) => {
    try {
      await likePost(id);
      fetchPosts();
    } catch {
      // silent
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    router.push('/login');
  };

  if (loading) return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center">
      <div className="text-gray-400">Loading...</div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-950">
      <nav className="bg-gray-900 border-b border-gray-800 px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <h1 className="text-xl font-bold text-white">Dev<span className="text-blue-500">Connect</span></h1>
        <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-white transition">Logout</button>
      </nav>

      <div className="max-w-2xl mx-auto px-4 py-6 space-y-4">
        <div className="bg-gray-900 rounded-2xl p-4 border border-gray-800">
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Share something with the dev community..."
            rows={3}
            className="w-full bg-gray-800 text-white text-sm rounded-lg px-4 py-3 border border-gray-700 focus:outline-none focus:border-blue-500 resize-none transition"
          />
          <div className="flex justify-end mt-2">
            <button
              onClick={handlePost}
              disabled={posting || !content.trim()}
              className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-800 disabled:cursor-not-allowed text-white text-sm font-semibold px-5 py-2 rounded-lg transition"
            >
              {posting ? 'Posting...' : 'Post'}
            </button>
          </div>
        </div>

        {posts.length === 0 ? (
          <div className="text-center text-gray-500 py-12">No posts yet. Be the first to post!</div>
        ) : (
          posts.map((post) => (
            <div key={post.id} className="bg-gray-900 rounded-2xl p-5 border border-gray-800">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-9 h-9 rounded-full bg-blue-600 flex items-center justify-center text-white text-sm font-bold">
                  {post.authorUsername?.[0]?.toUpperCase() || 'U'}
                </div>
                <div>
                  <p className="text-white text-sm font-semibold">{post.authorUsername || 'Unknown'}</p>
                  <p className="text-gray-500 text-xs">{new Date(post.createdAt).toLocaleDateString()}</p>
                </div>
              </div>
              <p className="text-gray-300 text-sm leading-relaxed">{post.content}</p>
              <div className="mt-3">
                <button
                  onClick={() => handleLike(post.id)}
                  className="flex items-center gap-1 text-gray-500 hover:text-blue-400 text-sm transition"
                >
                  ♥ <span>{post.likeCount || 0}</span>
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}